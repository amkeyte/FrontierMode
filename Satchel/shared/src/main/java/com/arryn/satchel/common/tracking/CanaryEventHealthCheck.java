package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.LogicalSide;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.SatchelEventBus;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The canary's pair of module events, end to end (SAT_113) -- see {@link SatchelHealth} and the health-canary
 * example. Both are server only, on real loader events:
 * <ul>
 *   <li><b>A scoped event, {@link CanaryHurt}</b> on the hurt event, resolved with {@code Scopes.of} on the health mob
 *   jig, which admits only the canary. The canary's mob-scope Trinket hears it with {@code info.listenFor}. The probe
 *   hurts the canary once for no damage, after the other probes have finished with it, and expects exactly one built
 *   and one delivered to the canary's own scope. One built for any other scope, in any hurt, is a violation. Its
 *   amount is read live (SAT_189): a consumer ahead of the canary's changes it, the canary's consumer reads the change,
 *   a consumer after both puts it back, and a read after dispatch throws;</li>
 *   <li><b>An unscoped event, {@link CanaryNamespaceDied}</b> on the death event, which tests whether the mob carries
 *   the canary's {@code MobData} namespace ({@link CanaryData}). The probe adds two throwaway bats, one marked and one
 *   not, and kills each in the same call, so the next canary check cannot discard them first. The marked one must
 *   arrive as a canary and the other not. The canary itself is never killed.</li>
 * </ul>
 * One line per world.
 *
 * <p>
 * The check is shared. Each line's {@code CanaryEventIngress} implements the two event interfaces on its loader's
 * hurt and death events, resolves the hurt with {@link CanaryEventHealthCheck#resolveHurt(Entity)}, calls
 * {@link CanaryEventHealthCheck#onHurtBuilt(ScopeInfo)} from its hurt event's constructor, and installs
 * {@link CanaryEventHealthCheck#consumers()}.
 */
final class CanaryEventHealthCheck {

    private CanaryEventHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** The amount the first consumer sets on every canary hurt, which the canary's consumer must read. */
    private static final float MARK = 0.25F;

    /** Contributes the driver. Each line registers the ingress. */
    static void register() {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                EventDriver.class, EventDriver::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Canary event " + message);
    }

    // =================================================================
    // The module events, and what a line's adapter calls
    // =================================================================

    /**
     * Scoped: resolved to the canary's mob scope, so only the canary ever reaches it.
     * Fires before armor, where a consumer may change the damage.
     */
    interface CanaryHurt extends ModuleEvent<ScopeInfo> {

        default ScopeInfo canary() {
            return context();
        }

        /** The damage the hurt deals, read live: a consumer before this one may have changed it. */
        float amount();

        /** Changes the damage the hurt deals. */
        void setAmount(float amount);
    }

    /** Unscoped: any mob carrying the canary's namespace, with no scope to resolve to. */
    interface CanaryNamespaceDied extends ModuleEvent<Void> {

        /** The mob that died, copied when the event was built. */
        UUID mob();

        /** Whether it carried the canary's mark, copied when the event was built. */
        boolean isCanary();
    }

    /** The resolver of {@link CanaryHurt}: counts the firing and finds the hurt mob's scope on the health mob jig. */
    static Optional<ScopeInfo> resolveHurt(Entity hurt) {
        SatchelHealth.state().canaryHurtFired.incrementAndGet();
        return Scopes.of(MobHealthCheck.MOB_JIG, hurt);
    }

    /** From a {@link CanaryHurt}'s constructor: counts it, and checks it was built for the canary. */
    static void onHurtBuilt(ScopeInfo canary) {
        SatchelHealth.state().canaryHurtBuilt.incrementAndGet();

        UUID known = SatchelHealth.canaryUuid().orElse(null);
        if (!ScopeInfoImpl.of(canary).scopeId().equals(known)) {
            violation("a CanaryHurt was built for " + canary.debugName() + ", which is not the canary");
        }
    }

    /**
     * The global consumers each line's ingress installs: the unscoped death's consumer, and the two that bracket the
     * canary's own consumer of a hurt to show its amount is read live.
     */
    static GlobalConsumers consumers() {
        GlobalConsumers consumers = new GlobalConsumers();

        consumers.listenForAll(CanaryHurt.class, e -> {
            SatchelHealth.state().canaryHurtAmountBefore = e.amount();
            e.setAmount(MARK);
        }).priority(SatchelEventBus.Priority.HIGH);
        consumers.listenForAll(CanaryHurt.class, e -> e.setAmount(SatchelHealth.state().canaryHurtAmountBefore))
                .priority(SatchelEventBus.Priority.LOW);

        consumers.listenForAll(CanaryNamespaceDied.class, d -> {
            HealthState state = SatchelHealth.state();
            if (state.deathWatch.contains(d.mob())) state.deathSeen.put(d.mob(), d.isCanary());
        });
        return consumers;
    }

    /**
     * Called by the canary's mob-scope Trinket as it attaches: the scope-bound consumer of {@link CanaryHurt}, removed
     * when the scope unloads. The event is server only, so a client has none. It runs between the two global consumers
     * above, so the amount it reads is the one the first set.
     */
    static void listen(ScopeInfoImpl info) {
        if (info.jigInfo().side() != LogicalSide.SERVER) return;

        info.listenFor(CanaryHurt.class, e -> {
            HealthState state = SatchelHealth.state();
            state.canaryHurtDelivered.incrementAndGet();
            if (e.canary() != info) {
                violation("a CanaryHurt resolved to " + e.canary().debugName() + " reached the consumer of "
                        + info.debugName());
            }
            if (e.amount() != MARK) {
                violation("the canary's consumer read an amount of " + e.amount() + " after an earlier consumer "
                        + "set " + MARK + ", so the amount was not read live");
            }
            state.canaryHurtKept = e;
        });
    }

    // =================================================================
    // The driver
    // =================================================================

    /**
     * On the server's level host, in the Overworld: once the hot-event and unload probes have finished with the
     * canary, hurts it once for no damage and checks the scoped event and its live amount, then kills the two
     * throwaway bats and checks the unscoped one.
     */
    public static final class EventDriver implements Ticking {

        /** Ticks between tries, and how many tries, when a hurt does not reach the event. */
        private static final long RETRY_EVERY = 100;
        private static final int MAX_ATTEMPTS = 5;

        private int attempts;
        private long nextAttempt;

        @Override
        public void onTickAfter(ScopeInfo info) {
            ScopeInfoImpl impl = ScopeInfoImpl.of(info);
            if (!(info.scope() instanceof LevelScope levelScope)) return;
            Level level = levelScope.level();
            if (!level.dimension().equals(Level.OVERWORLD)) return;

            HealthState state = SatchelHealth.state();
            if (!state.logged.contains("hurt:done") || !state.logged.contains("mobunload:released:SERVER")) return;
            if (state.logged.contains("canaryevents:done") || attempts >= MAX_ATTEMPTS) return;

            long now = impl.jigInfo().trinketTick().tickNumber();
            if (now < nextAttempt) return;

            Optional<UUID> canary = SatchelHealth.canaryUuid();
            if (canary.isEmpty()) return;
            Optional<Entity> found = impl.jigInfo().foundation().egress().getEntity(level, canary.get());
            if (found.isEmpty() || !(found.get() instanceof LivingEntity living)) return;

            // A second hurt inside the invulnerability window is dropped before any event.
            if (living.invulnerableTime > 10) return;

            attempts++;
            nextAttempt = now + RETRY_EVERY;
            long firedBefore = state.canaryHurtFired.get();
            long builtBefore = state.canaryHurtBuilt.get();
            long deliveredBefore = state.canaryHurtDelivered.get();
            state.canaryHurtKept = null;

            living.hurt(level.damageSources().generic(), 0.0F);

            if (state.canaryHurtFired.get() == firedBefore) {
                if (attempts >= MAX_ATTEMPTS) {
                    OUT.warn("[SatchelHealth] Canary-event probe: " + MAX_ATTEMPTS + " hurts of the canary never "
                            + "reached the hurt event, so the scoped event was not exercised");
                }
                return;
            }
            long built = state.canaryHurtBuilt.get() - builtBefore;
            long delivered = state.canaryHurtDelivered.get() - deliveredBefore;
            if (built != 1 || delivered != 1) {
                violation("one hurt of the canary built " + built + " and delivered " + delivered
                        + " CanaryHurt(s) to its scope, expected exactly 1 of each");
            }
            expectClosed(state.canaryHurtKept);

            killTwoBats(level, state);

            if (state.logged.add("canaryevents:done")) {
                OUT.info("[SatchelHealth] Canary-event probe: a hurt event resolved with Scopes.of on the mob jig "
                        + "built and delivered exactly 1 CanaryHurt, to the canary's own scope, and its consumer read "
                        + "the amount an earlier consumer set, live, which threw when read after dispatch; a bat "
                        + "carrying the canary's MobData namespace died as a canary and one without it as not, "
                        + "through the unscoped event");
            }
        }

        /** The hurt the canary's consumer kept: dispatch has ended, so its live read must throw. */
        private static void expectClosed(CanaryHurt kept) {
            if (kept == null) return;   // not delivered: already a violation above
            boolean threw = false;
            try {
                kept.amount();
            } catch (IllegalStateException expected) {
                threw = true;
            }
            if (!threw) violation("a CanaryHurt kept past its dispatch still read its amount");
        }

        private static void killTwoBats(Level level, HealthState state) {
            Mob marked = MobHealthCheck.createSyntheticMob(level);
            Mob plain = MobHealthCheck.createSyntheticMob(level);
            if (marked == null || plain == null) {
                OUT.warn("[SatchelHealth] Canary-event probe: could not create the throwaway bats, so the "
                        + "unscoped event was not exercised");
                return;
            }
            CanaryData.mark(marked);

            kill(level, state, marked);
            kill(level, state, plain);

            expect(state, marked, true);
            expect(state, plain, false);
        }

        /** Adds {@code mob} and kills it in this call: the unscoped consumer runs inside {@code kill()}. */
        private static void kill(Level level, HealthState state, Mob mob) {
            state.deathWatch.add(mob.getUUID());
            if (!level.addFreshEntity(mob)) {
                violation("a throwaway bat was refused a join nothing should have refused");
                return;
            }
            mob.kill();
        }

        private static void expect(HealthState state, Mob mob, boolean canary) {
            UUID id = mob.getUUID();
            state.deathWatch.remove(id);
            Boolean seen = state.deathSeen.remove(id);
            String what = canary ? "a bat carrying the canary's namespace" : "a bat without it";

            if (seen == null) {
                violation(what + " was killed (dying: " + mob.isDeadOrDying() + ") but no CanaryNamespaceDied "
                        + "reached the unscoped consumer");
            } else if (seen != canary) {
                violation(what + " reached the unscoped consumer as " + (seen ? "a canary" : "not a canary"));
            }
        }
    }
}
