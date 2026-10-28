package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.Optional;
import java.util.UUID;

/**
 * The canary's pair of module events, end to end (SAT_113) -- see {@link SatchelHealth} and the
 * health-canary example. Both are server only, on real Forge events:
 * <ul>
 *   <li><b>A scoped event, {@code CanaryHurt}</b> on {@code LivingHurtEvent}, resolved with {@code
 *   Scopes.of} on the health mob jig, which admits only the canary. The canary's mob-scope Trinket
 *   hears it with {@code info.listenFor}. The probe hurts the canary once for no damage, after the
 *   other probes have finished with it, and expects exactly one built and one delivered to the canary's
 *   own scope. One built for any other scope, in any hurt, is a violation;</li>
 *   <li><b>An unscoped event, {@code CanaryNamespaceDied}</b> on {@code LivingDeathEvent}, which tests
 *   whether the mob carries the canary's {@code MobData} namespace ({@link CanaryData}). The probe adds
 *   two throwaway bats, one marked and one not, and kills each in the same call, so the next canary
 *   check cannot discard them first. The marked one must arrive as a canary and the other not. The
 *   canary itself is never killed.</li>
 * </ul>
 * One line per world.
 */
final class CanaryEventHealthCheck {

    private CanaryEventHealthCheck() {}

    private static final String MODULE = "satchel_health";

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:canary_events");

    static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                EventDriver.class, EventDriver::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Canary event " + message);
    }

    // =================================================================
    // The module events
    // =================================================================

    /** Scoped: resolved to the canary's mob scope, so only the canary ever reaches it. */
    static final class CanaryHurt extends ModuleSignal<LivingHurtEvent, ScopeInfo> {
        CanaryHurt(LivingHurtEvent e, ScopeInfoImpl canary) {
            super(e, canary);
            SatchelHealth.state().canaryHurtBuilt.incrementAndGet();

            UUID known = SatchelHealth.canaryUuid().orElse(null);
            if (!canary.scopeId().equals(known)) {
                violation("a CanaryHurt was built for " + canary.debugName() + ", which is not the canary");
            }
        }

        ScopeInfo canary() {
            return context();
        }
    }

    /** Unscoped: any mob carrying the canary's namespace, with no scope to resolve to. */
    static final class CanaryNamespaceDied extends ModuleSignal<LivingDeathEvent, Void> {
        private final UUID mob;          // fact, copied
        private final boolean canary;    // fact, copied

        CanaryNamespaceDied(LivingDeathEvent e) {
            super(e, null);
            mob = e.getEntity().getUUID();
            canary = CanaryData.isCanary(e.getEntity());
        }

        UUID mob() {
            return mob;
        }

        boolean isCanary() {
            return canary;
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);

        ingress.translate(LivingHurtEvent.class, e -> {
            SatchelHealth.state().canaryHurtFired.incrementAndGet();
            return Scopes.of(MobHealthCheck.MOB_JIG, e.getEntity());
        }, CanaryHurt::new);

        ingress.translate(LivingDeathEvent.class, CanaryNamespaceDied::new);
        ingress.listenForAll(CanaryNamespaceDied.class, d -> {
            HealthState state = SatchelHealth.state();
            if (state.deathWatch.contains(d.mob())) state.deathSeen.put(d.mob(), d.isCanary());
        });

        return ingress;
    }

    /**
     * Called by the canary's mob-scope Trinket as it attaches: the scope-bound consumer of {@code
     * CanaryHurt}, removed when the scope unloads. The event is server only, so a client has none.
     */
    static void listen(ScopeInfoImpl info) {
        if (info.jigInfo().side() != LogicalSide.SERVER) return;

        info.listenFor(CanaryHurt.class, e -> {
            SatchelHealth.state().canaryHurtDelivered.incrementAndGet();
            if (e.canary() != info) {
                violation("a CanaryHurt resolved to " + e.canary().debugName() + " reached the consumer of "
                        + info.debugName());
            }
        });
    }

    // =================================================================
    // The driver
    // =================================================================

    /**
     * On the server's level host, in the Overworld: once the hot-event and unload probes have finished
     * with the canary, hurts it once for no damage and checks the scoped event, then kills the two
     * throwaway bats and checks the unscoped one.
     */
    public static final class EventDriver implements Ticking {

        /** Ticks between tries, and how many tries, when a hurt does not reach the event. */
        private static final long RETRY_EVERY = 100;
        private static final int MAX_ATTEMPTS = 5;

        private int attempts;
        private long nextAttempt;

        @Override
        public void onTickAfter(ScopeInfoImpl info) {
            if (!(info.scope() instanceof LevelScope levelScope)) return;
            if (!(levelScope.level() instanceof ServerLevel level)) return;
            if (!level.dimension().equals(Level.OVERWORLD)) return;

            HealthState state = SatchelHealth.state();
            if (!state.logged.contains("hurt:done") || !state.logged.contains("mobunload:released:SERVER")) return;
            if (state.logged.contains("canaryevents:done") || attempts >= MAX_ATTEMPTS) return;

            long now = info.jigInfo().trinketTick().tickNumber();
            if (now < nextAttempt) return;

            Optional<UUID> canary = SatchelHealth.canaryUuid();
            if (canary.isEmpty()) return;
            Optional<Entity> found = info.jigInfo().foundation().egress().getEntity(level, canary.get());
            if (found.isEmpty() || !(found.get() instanceof LivingEntity living)) return;

            // A second hurt inside the invulnerability window is dropped before any event.
            if (living.invulnerableTime > 10) return;

            attempts++;
            nextAttempt = now + RETRY_EVERY;
            long firedBefore = state.canaryHurtFired.get();
            long builtBefore = state.canaryHurtBuilt.get();
            long deliveredBefore = state.canaryHurtDelivered.get();

            living.hurt(level.damageSources().generic(), 0.0F);

            if (state.canaryHurtFired.get() == firedBefore) {
                if (attempts >= MAX_ATTEMPTS) {
                    OUT.warn("[SatchelHealth] Canary-event probe: " + MAX_ATTEMPTS + " hurts of the canary never "
                            + "reached LivingHurtEvent, so the scoped event was not exercised");
                }
                return;
            }
            long built = state.canaryHurtBuilt.get() - builtBefore;
            long delivered = state.canaryHurtDelivered.get() - deliveredBefore;
            if (built != 1 || delivered != 1) {
                violation("one hurt of the canary built " + built + " and delivered " + delivered
                        + " CanaryHurt(s) to its scope, expected exactly 1 of each");
            }

            killTwoBats(level, state);

            if (state.logged.add("canaryevents:done")) {
                OUT.info("[SatchelHealth] Canary-event probe: a LivingHurtEvent resolved with Scopes.of on the mob "
                        + "jig built and delivered exactly 1 CanaryHurt, to the canary's own scope; a bat carrying the "
                        + "canary's MobData namespace died as a canary and one without it as not, through the "
                        + "unscoped event");
            }
        }

        private static void killTwoBats(ServerLevel level, HealthState state) {
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
        private static void kill(ServerLevel level, HealthState state, Mob mob) {
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
