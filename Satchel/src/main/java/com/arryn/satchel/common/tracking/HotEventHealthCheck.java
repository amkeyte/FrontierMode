package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * A resolver on a hot event (SAT_134 probe 4) -- see {@link SatchelHealth}. {@code LivingHurtEvent}
 * fires for every hit on every living thing, so a module registers it behind a resolver that is a
 * cheap filter, and the module event is built only when the resolver answers. Two registrations on it:
 * <ul>
 *   <li><b>a resolver that answers empty for everything</b>, with a subscriber on its event, so the
 *   resolver and not the no-subscriber check is what keeps the constructor from running. A constructor
 *   counter stays at 0 across live play, however many hurts the resolver sees;</li>
 *   <li><b>a resolver that answers for one tagged probe entity</b>, which builds exactly one event
 *   and delivers it once per hurt the probe makes.</li>
 * </ul>
 * The tagged entity is the canary, hurt once by the probe for no damage: the event fires before Forge
 * discards a zero amount. One line per world.
 */
final class HotEventHealthCheck {

    private HotEventHealthCheck() {}

    private static final String MODULE = "satchel_health";

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:hot_events");

    /** The scoreboard tag the probe puts on the entity it hurts. */
    private static final String TAG = "satchelhealth_hurt_probe";

    static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                HurtDriver.class, HurtDriver::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Hot event " + message);
    }

    // =================================================================
    // The module events
    // =================================================================

    /** Scoped to a context nothing resolves: must never be built. */
    static final class HurtNever extends ModuleSignal<LivingHurtEvent, Integer> {
        HurtNever(LivingHurtEvent e, Integer context) {
            super(e, context);
            SatchelHealth.state().hurtNeverBuilt.incrementAndGet();
            violation("an event whose resolver answered empty was built anyway");
        }
    }

    /** The context is the tagged entity's UUID: a plain value, since the resolver is only a filter. */
    static final class HurtTagged extends ModuleSignal<LivingHurtEvent, UUID> {
        HurtTagged(LivingHurtEvent e, UUID context) {
            super(e, context);
            SatchelHealth.state().hurtTaggedBuilt.incrementAndGet();
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);

        // Registered across two natives' worth of firings; what each proves is in the class doc.
        ingress.receiveCancelled().translate(LivingHurtEvent.class, e -> {
            SatchelHealth.state().hurtResolved.incrementAndGet();
            return Optional.<Integer>empty();
        }, HurtNever::new);
        ingress.listenForAll(HurtNever.class, e -> { }).receiveCancelled();

        ingress.receiveCancelled().translate(LivingHurtEvent.class,
                e -> e.getEntity().getTags().contains(TAG)
                        ? Optional.of(e.getEntity().getUUID())
                        : Optional.<UUID>empty(),
                HurtTagged::new);
        ingress.listenForAll(HurtTagged.class, e -> SatchelHealth.state().hurtTaggedDelivered.incrementAndGet())
                .receiveCancelled();

        return ingress;
    }

    // =================================================================
    // The driver
    // =================================================================

    /**
     * On the server's level host, in the Overworld: once the canary exists, tags it and hurts it for
     * zero damage, then checks that the tagged resolver built and delivered exactly one event and the
     * empty one built none. Afterwards it keeps checking that none is ever built.
     */
    public static final class HurtDriver implements Ticking {

        /** Ticks between tries, and how many tries, when a hurt does not reach the event. */
        private static final long RETRY_EVERY = 100;
        private static final int MAX_ATTEMPTS = 5;

        private int attempts;
        private long nextAttempt;

        @Override
        public void onTickAfter(ScopeInfo info) {
            ScopeInfoImpl impl = ScopeInfoImpl.of(info);
            if (!(info.scope() instanceof LevelScope levelScope)) return;
            if (!(levelScope.level() instanceof ServerLevel level)) return;
            if (!level.dimension().equals(Level.OVERWORLD)) return;

            HealthState state = SatchelHealth.state();
            if (state.hurtNeverBuilt.get() != 0) {
                violation(state.hurtNeverBuilt.get() + " event(s) were built by a resolver that answers empty");
            }
            if (state.logged.contains("hurt:done") || attempts >= MAX_ATTEMPTS) return;

            long now = impl.jigInfo().trinketTick().tickNumber();
            if (now < nextAttempt) return;

            Optional<UUID> canary = SatchelHealth.canaryUuid();
            if (canary.isEmpty()) return;
            Optional<Entity> found = impl.jigInfo().foundation().egress().getEntity(level, canary.get());
            if (found.isEmpty() || !(found.get() instanceof LivingEntity living)) return;

            // Hurt twice inside the invulnerability window and Minecraft drops the second before any
            // event; wait out another hit rather than count a firing that cannot happen.
            if (living.invulnerableTime > 10) return;

            attempts++;
            nextAttempt = now + RETRY_EVERY;
            living.addTag(TAG);
            long builtBefore = state.hurtTaggedBuilt.get();
            long deliveredBefore = state.hurtTaggedDelivered.get();
            long resolvedBefore = state.hurtResolved.get();

            living.hurt(level.damageSources().generic(), 0.0F);

            long built = state.hurtTaggedBuilt.get() - builtBefore;
            long delivered = state.hurtTaggedDelivered.get() - deliveredBefore;
            if (state.hurtResolved.get() == resolvedBefore) {
                // The hurt never reached the event (another listener stopped it first, say): try again
                // later, and say so once if it never does.
                if (attempts >= MAX_ATTEMPTS) {
                    OUT.warn("[SatchelHealth] Hot-event probe: " + MAX_ATTEMPTS + " hurts of the canary never "
                            + "reached LivingHurtEvent, so the tagged resolver was not exercised");
                }
                return;
            }
            if (built != 1 || delivered != 1) {
                violation("one hurt of the tagged entity built " + built + " and delivered " + delivered
                        + " event(s), expected exactly 1 of each");
            }

            if (state.logged.add("hurt:done")) {
                OUT.info("[SatchelHealth] Hot-event probe: a LivingHurtEvent resolver for one tagged entity built "
                        + "and delivered exactly 1 event for 1 hurt, and the resolver that answers empty "
                        + "built " + state.hurtNeverBuilt.get() + " across " + state.hurtResolved.get()
                        + " firing(s) (it keeps counting; any build is a violation)");
            }
        }
    }
}
