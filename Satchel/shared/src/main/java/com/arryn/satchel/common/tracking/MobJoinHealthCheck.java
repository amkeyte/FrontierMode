package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.JigInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * A scope is built only for a mob that has joined the level (SAT_135) -- see {@link SatchelHealth}. The loader posts
 * a mob's own join event before the mob is in the level, where the join can still be cancelled, so a module event on it
 * resolved with {@code Scopes.of} on a mob jig must find no scope for the mob. The server probe adds a synthetic mob of
 * its own, once per world, to the health mob jig's interest and checks, with a listener that cancels the join after the
 * module event has been resolved:
 * <ul>
 *   <li><b>a cancelled join</b> -- no scope is built during the join (the module event is never delivered), and none
 *   exists after it;</li>
 *   <li><b>an allowed join</b> -- no scope during the join either, and the scope is there as soon as
 *   {@code addFreshEntity} has returned;</li>
 *   <li><b>a copy with the same UUID</b> -- asked for a scope, it gets the original's, the dimension change case, and
 *   the original's leave unloads it.</li>
 * </ul>
 *
 * <p>
 * The check is shared. Each line's {@code MobJoinIngress} translates its loader's join event into
 * {@link JoinedProbeMob} with {@link MobJoinHealthCheck#resolveJoin(Entity)} as the resolver, first among the
 * listeners, installs {@link MobJoinHealthCheck#consumers()}, and cancels a join last among the listeners when
 * {@link MobJoinHealthCheck#refusesJoin(Entity)} says to.
 */
final class MobJoinHealthCheck {

    private MobJoinHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** Marks a mob the probe made, so the resolver and the canceller leave every other mob alone. */
    private static final String TAG = "satchelhealth_join_probe";

    /** Marks the one whose join the canceller refuses. */
    private static final String CANCEL_TAG = "satchelhealth_join_probe_cancel";

    /** Ticks the probe waits after the Overworld scope is ticking, so the world is settled. */
    private static final int RUN_AFTER_TICKS = 100;

    /** Contributes the probe. Each line registers the ingress and the canceller. */
    static void register() {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                JoinProbe.class, JoinProbe::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Mob join " + message);
    }

    // =================================================================
    // The module event, and what a line's adapter calls
    // =================================================================

    /** Scoped to the mob jig's scope for a probe mob: must never be built, since the mob has not joined. */
    interface JoinedProbeMob extends ModuleEvent<ScopeInfo> {
    }

    /** The resolver of {@link JoinedProbeMob}: the health mob jig's scope for a probe mob, else empty. */
    static Optional<ScopeInfo> resolveJoin(Entity joined) {
        return joined instanceof Mob mob && mob.getTags().contains(TAG)
                ? Scopes.of(MobHealthCheck.MOB_JIG, mob)
                : Optional.empty();
    }

    /** Whether the canceller refuses this join: only the probe mob marked for it. */
    static boolean refusesJoin(Entity joining) {
        return joining.getTags().contains(CANCEL_TAG);
    }

    /** The global consumers each line's ingress installs. */
    static GlobalConsumers consumers() {
        GlobalConsumers consumers = new GlobalConsumers();
        consumers.listenForAll(JoinedProbeMob.class, e -> SatchelHealth.state().joinDelivered.incrementAndGet())
                .receiveCancelled();
        return consumers;
    }

    // =================================================================
    // The probe
    // =================================================================

    /**
     * On the server's level host, in the Overworld: runs the three cases once per world.
     */
    public static final class JoinProbe implements Ticking {

        private int ticks;

        @Override
        public void onTickAfter(ScopeInfo info) {
            Level level = info.level();
            if (!level.dimension().equals(Level.OVERWORLD)) return;
            if (++ticks < RUN_AFTER_TICKS) return;
            if (!SatchelHealth.firstTime("mobjoin:run")) return;

            run(info, level);
        }

        private static void run(ScopeInfo info, Level level) {
            MobJig jig = Satchel.mobJig(MobHealthCheck.MOB_JIG);
            JigInfo jigInfo = ScopeInfoImpl.of(info).jigInfo().foundation().requireJigInfo(MobHealthCheck.MOB_JIG);
            HealthState state = SatchelHealth.state();
            long deliveredBefore = state.joinDelivered.get();

            // ---- a cancelled join ----
            Mob refused = probeMob(level, true);
            if (refused == null) {
                OUT.warn("[SatchelHealth] Mob join probe: could not create a synthetic mob, so it was not run");
                return;
            }
            UUID refusedId = refused.getUUID();
            jig.interest(refusedId);   // the mob is not in the level, so this records the entry and builds nothing
            boolean refusedAdded = level.addFreshEntity(refused);
            if (refusedAdded) {
                violation("the probe's cancelling listener did not cancel the join, so the case did not run");
                refused.discard();
            } else if (jigInfo.hasScope(refusedId) || jig.scopeFor(refusedId).isPresent()) {
                violation("a mob whose join was cancelled has a scope");
            }
            jig.lostInterest(refusedId);

            // ---- an allowed join ----
            Mob joined = probeMob(level, false);
            if (joined == null) return;
            UUID joinedId = joined.getUUID();
            jig.interest(joinedId);
            if (!level.addFreshEntity(joined)) {
                violation("the probe's mob was refused a join nothing should have refused");
                jig.lostInterest(joinedId);
                return;
            }
            if (jig.scopeFor(joinedId).isEmpty()) {
                violation("a mob that has joined, and is of interest, has no scope once addFreshEntity returned");
            }

            // ---- a copy with the same UUID (the dimension change) ----
            Mob copy = probeMob(level, false);
            if (copy != null) {
                copy.setUUID(joinedId);
                Optional<ScopeInfo> viaCopy = jig.infoFor(copy);
                if (viaCopy.isEmpty() || !(viaCopy.get().scope() instanceof MobScope scope) || scope.mob() != joined) {
                    violation("a copy with the original's UUID did not get the original's scope");
                }
            }

            // No module event was delivered for either probe mob's own join.
            long delivered = state.joinDelivered.get() - deliveredBefore;
            if (delivered != 0) {
                violation(delivered + " module event(s) were delivered for a probe mob's own join, which a scope "
                        + "built for a mob that has not joined would have allowed");
            }

            // Its leave unloads the scope.
            joined.discard();
            if (jigInfo.hasScope(joinedId)) {
                violation("the probe's mob left the level but its scope is still there");
            }
            jig.lostInterest(joinedId);

            OUT.info("[SatchelHealth] Mob join probe: no scope was built for a mob during its own join, none exists "
                    + "for one whose join was cancelled, the scope is there once an allowed join has returned, and "
                    + "a copy with the same UUID gets the original's scope");
        }

        /** A synthetic mob, tagged for this probe, not yet added to the level. */
        private static Mob probeMob(Level level, boolean cancelJoin) {
            Mob mob = MobHealthCheck.createSyntheticMob(level);
            if (mob == null) return null;
            mob.addTag(TAG);
            if (cancelJoin) mob.addTag(CANCEL_TAG);
            return mob;
        }
    }
}
