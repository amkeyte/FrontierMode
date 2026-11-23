package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * A resolver on a hot event (SAT_134 probe 4) -- see {@link SatchelHealth}. The hurt event fires for every hit on
 * every living thing, so a module registers it behind a resolver that is a cheap filter, and the module event is built
 * only when the resolver answers. Two registrations on it:
 * <ul>
 *   <li><b>a resolver that answers empty for everything</b>, with a subscriber on its event, so the resolver and not
 *   the no-subscriber check is what keeps the constructor from running. A constructor counter stays at 0 across live
 *   play, however many hurts the resolver sees;</li>
 *   <li><b>a resolver that answers for one tagged probe entity</b>, which builds exactly one event and delivers it once
 *   per hurt the probe makes.</li>
 * </ul>
 * The tagged entity is the canary, hurt once by the probe for no damage. One line per world.
 *
 * <p>
 * The check is shared. Each line's {@code HotEventIngress} translates its loader's hurt event into {@link HurtNever}
 * and {@link HurtTagged}, with {@link HotEventHealthCheck#resolveNothing()} and
 * {@link HotEventHealthCheck#resolveTagged(Entity)} as the resolvers, calls {@link HotEventHealthCheck#onNeverBuilt()}
 * and {@link HotEventHealthCheck#onTaggedBuilt()} from its events' constructors, and installs
 * {@link HotEventHealthCheck#consumers()}.
 */
final class HotEventHealthCheck {

    private HotEventHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** The scoreboard tag the probe puts on the entity it hurts. */
    private static final String TAG = "satchelhealth_hurt_probe";

    /** Contributes the driver. Each line registers the ingress. */
    static void register() {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                HurtDriver.class, HurtDriver::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Hot event " + message);
    }

    // =================================================================
    // The module events, and what a line's adapter calls
    // =================================================================

    /**
     * Scoped to a context nothing resolves: must never be built.
     * Fires before armor; a consumer only reacts.
     */
    interface HurtNever extends ModuleEvent<Integer> {
    }

    /**
     * The context is the tagged entity's UUID: a plain value, since the resolver is only a filter.
     * Fires before armor; a consumer only reacts.
     */
    interface HurtTagged extends ModuleEvent<UUID> {
    }

    /** The resolver of {@link HurtNever}: counts the firing and answers empty. */
    static Optional<Integer> resolveNothing() {
        SatchelHealth.state().hurtResolved.incrementAndGet();
        return Optional.empty();
    }

    /** The resolver of {@link HurtTagged}: the hurt entity's UUID if the probe tagged it, else empty. */
    static Optional<UUID> resolveTagged(Entity hurt) {
        return hurt.getTags().contains(TAG) ? Optional.of(hurt.getUUID()) : Optional.empty();
    }

    /** From a {@link HurtNever}'s constructor, which must never run. */
    static void onNeverBuilt() {
        SatchelHealth.state().hurtNeverBuilt.incrementAndGet();
        violation("an event whose resolver answered empty was built anyway");
    }

    /** From a {@link HurtTagged}'s constructor. */
    static void onTaggedBuilt() {
        SatchelHealth.state().hurtTaggedBuilt.incrementAndGet();
    }

    /** The global consumers each line's ingress installs. */
    static GlobalConsumers consumers() {
        GlobalConsumers consumers = new GlobalConsumers();
        consumers.listenForAll(HurtNever.class, e -> { }).receiveCancelled();
        consumers.listenForAll(HurtTagged.class, e -> SatchelHealth.state().hurtTaggedDelivered.incrementAndGet())
                .receiveCancelled();
        return consumers;
    }

    // =================================================================
    // The driver
    // =================================================================

    /**
     * On the server's level host, in the Overworld: once the canary exists, tags it and hurts it for zero damage, then
     * checks that the tagged resolver built and delivered exactly one event and the empty one built none. Afterwards it
     * keeps checking that none is ever built.
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
            Level level = levelScope.level();
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
                            + "reached the hurt event, so the tagged resolver was not exercised");
                }
                return;
            }
            if (built != 1 || delivered != 1) {
                violation("one hurt of the tagged entity built " + built + " and delivered " + delivered
                        + " event(s), expected exactly 1 of each");
            }

            if (state.logged.add("hurt:done")) {
                OUT.info("[SatchelHealth] Hot-event probe: a hurt-event resolver for one tagged entity built "
                        + "and delivered exactly 1 event for 1 hurt, and the resolver that answers empty "
                        + "built " + state.hurtNeverBuilt.get() + " across " + state.hurtResolved.get()
                        + " firing(s) (it keeps counting; any build is a violation)");
            }
        }
    }
}
