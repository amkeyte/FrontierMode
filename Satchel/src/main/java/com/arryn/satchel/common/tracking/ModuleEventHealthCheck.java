package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.lifecycle.SatchelEventBus;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.LogicalSide;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The module-event slice of SatchelHealth (SAT_113) -- see {@link SatchelHealth}. A module like any
 * other: one ingress config registered for both sides, and a Trinket contributed to the level host.
 * It confirms, loudly (a failure is logged as a violation and the run goes on), on a real
 * client and server:
 * <ul>
 *   <li><b>a firing on the other side is dropped.</b> {@code EntityJoinLevelEvent} fires on both
 *   sides on an integrated server; the unscoped event records which side's level it came from, and
 *   its consumer fails if it runs on the other side's thread;</li>
 *   <li><b>an entity with no scope is bounced before the event is built.</b> A registration whose
 *   resolver always answers empty must never construct its event;</li>
 *   <li><b>cheap rejection.</b> An unscoped event nobody subscribes to is built at most once per side,
 *   to learn its class, and not for later firings;</li>
 *   <li><b>priority order.</b> Three global consumers subscribed out of order run high, normal,
 *   low;</li>
 *   <li><b>a native-event read after dispatch throws</b>, for the previous firing's event;</li>
 *   <li><b>scoped delivery reaches only the right scope</b>, through scope-bound consumers on each
 *   level host scope, and a consumer is removed when its scope unloads even though its Trinket never
 *   unsubscribes;</li>
 *   <li><b>{@code receiveCancelled}.</b> A synthetic cancelable native event is posted on each
 *   level host scope's own bus: a consumer that did not opt in is skipped once a higher one vetoes,
 *   one that opted in is not;</li>
 *   <li><b>the new-world spawn event reaches an unscoped global consumer.</b> Logged when a new world
 *   is created;</li>
 *   <li><b>a plain request event</b> (SAT_134 probe 5). An event of an unrelated class, not a module
 *   event, posted with the bus from inside another consumer's dispatch, two deep, reaches the
 *   {@code listenForAll} consumers of that exact class, in priority order.</li>
 * </ul>
 */
final class ModuleEventHealthCheck {

    private ModuleEventHealthCheck() {}

    private static final String MODULE = "satchel_health";

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:module_events");

    static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.BOTH, () -> build());
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.BOTH, Order.NORMAL,
                EventProbeTrinket.class, EventProbeTrinket::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Module event " + message);
    }

    // =================================================================
    // The module events
    // =================================================================

    /** Scoped: resolved to the level host scope of the level the entity joined. */
    static final class JoinInLevel extends ModuleSignal<EntityJoinLevelEvent, ScopeInfo> {
        private final boolean clientSide;   // fact, copied

        JoinInLevel(EntityJoinLevelEvent e, ScopeInfo level) {
            super(e, level);
            clientSide = e.getLevel().isClientSide();
        }

        boolean clientSide() {
            return clientSide;
        }
    }

    /** Unscoped: any entity joining any level. */
    static final class JoinAny extends ModuleSignal<EntityJoinLevelEvent, Void> {
        final List<String> trail = new ArrayList<>();   // which consumers ran, in order
        private final boolean clientSide;               // fact, copied

        JoinAny(EntityJoinLevelEvent e) {
            super(e, null);
            clientSide = e.getLevel().isClientSide();
        }

        boolean clientSide() {
            return clientSide;
        }

        /** Reads the native event, as a consumer that kept the event would. */
        Object readNativeNow() {
            return nativeEvent();
        }
    }

    /** Scoped to a context nothing resolves: must never be built. */
    static final class Never extends ModuleSignal<EntityJoinLevelEvent, Integer> {
        Never(EntityJoinLevelEvent e, Integer context) {
            super(e, context);
            violation("an event whose resolver answered empty was built anyway");
        }
    }

    /** Unscoped, with no subscriber: built once per side to learn its class, then never. */
    static final class Unwatched extends ModuleSignal<EntityJoinLevelEvent, Void> {
        Unwatched(EntityJoinLevelEvent e) {
            super(e, null);
            SatchelHealth.state().unwatchedBuilt.merge(LogicalSideContext.peek(), 1, Integer::sum);
        }
    }

    /** The new-world spawn position, unscoped: no scope exists to resolve to yet. */
    static final class WorldSpawn extends ModuleSignal<LevelEvent.CreateSpawnPosition, Void> {
        WorldSpawn(LevelEvent.CreateSpawnPosition e) {
            super(e, null);
        }
    }

    /**
     * Not a module event: a request posted with the bus by one consumer for others to answer. The bus
     * delivers any class to the consumers of that exact class.
     */
    static final class RequestProbe {
        final List<String> trail = new ArrayList<>();   // which consumers ran, in order
    }

    /** A native event of the probe's own, to carry a cancel state. */
    @Cancelable
    static final class ProbeNative extends Event {
    }

    /** Posted by the probe Trinket on its scope's side of the bus. */
    static final class ProbeSignal extends ModuleSignal<ProbeNative, ScopeInfo> {
        final boolean veto;
        final List<String> trail = new ArrayList<>();
        boolean verified;

        ProbeSignal(ProbeNative nativeEvent, ScopeInfo scope, boolean veto) {
            super(nativeEvent, scope);
            this.veto = veto;
        }

        void cancelNative() {
            nativeEvent().setCanceled(true);
        }

        void verify() {
            List<String> expected = veto
                    ? List.of("high", "low")                      // the veto skips the two that didn't opt in
                    : List.of("high", "normal", "scope", "low");  // the global one subscribed before the scope's
            if (!trail.equals(expected)) {
                violation("a " + (veto ? "vetoed" : "plain") + " synthetic event reached its consumers as "
                        + trail + ", expected " + expected);
            }
            verified = true;
        }
    }

    // =================================================================
    // The ingress
    // =================================================================

    /** Once per world; the key is prefixed because the world's printed-lines set is shared by every probe. */
    private static void logOnce(String key, String message) {
        SatchelHealth.logOnce("moduleevents:" + key, "[SatchelHealth] Module-event probe: " + message);
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);

        // Registered across two natives and six consumers; what each proves is in the class doc.
        ingress.translate(EntityJoinLevelEvent.class,
                e -> Scopes.of(HostJigs.LEVEL_HOST, e.getLevel()), JoinInLevel::new);
        ingress.translate(EntityJoinLevelEvent.class, JoinAny::new);
        ingress.translate(EntityJoinLevelEvent.class, e -> {
            SatchelHealth.state().joinBounced.merge(LogicalSideContext.peek(), 1, Integer::sum);
            return Optional.<Integer>empty();
        }, Never::new);
        ingress.translate(EntityJoinLevelEvent.class, Unwatched::new);
        ingress.translate(LevelEvent.CreateSpawnPosition.class, WorldSpawn::new);

        // Subscribed out of order on purpose: priority, not subscription order, decides.
        ingress.listenForAll(JoinAny.class, e -> e.trail.add("normal"));
        ingress.listenForAll(JoinAny.class, ModuleEventHealthCheck::lastJoinConsumer)
                .priority(SatchelEventBus.Priority.LOW);
        ingress.listenForAll(JoinAny.class, e -> e.trail.add("high"))
                .priority(SatchelEventBus.Priority.HIGH);

        // The request's consumers, also out of order; the request itself is posted from lastJoinConsumer.
        ingress.listenForAll(RequestProbe.class, e -> e.trail.add("normal"));
        ingress.listenForAll(RequestProbe.class, e -> e.trail.add("low"))
                .priority(SatchelEventBus.Priority.LOW);
        ingress.listenForAll(RequestProbe.class, e -> e.trail.add("high"))
                .priority(SatchelEventBus.Priority.HIGH);

        ingress.listenForAll(WorldSpawn.class, e -> logOnce("spawn:" + LogicalSideContext.peek(),
                "the new-world spawn event reached an unscoped global consumer on the "
                        + LogicalSideContext.peek() + " side"));

        ingress.listenForAll(ProbeSignal.class, e -> {
            e.trail.add("high");
            if (e.veto) e.cancelNative();
        }).priority(SatchelEventBus.Priority.HIGH);
        ingress.listenForAll(ProbeSignal.class, e -> e.trail.add("normal"));
        ingress.listenForAll(ProbeSignal.class, e -> {
            e.trail.add("low");
            e.verify();
        }).priority(SatchelEventBus.Priority.LOW).receiveCancelled();

        return ingress;
    }

    /** The last consumer of {@link JoinAny} in priority order: where the checks on a firing run. */
    private static void lastJoinConsumer(JoinAny e) {
        e.trail.add("low");
        LogicalSide side = LogicalSideContext.peek();
        HealthState state = SatchelHealth.state();

        if (!e.trail.equals(List.of("high", "normal", "low"))) {
            violation("global consumers ran as " + e.trail + " on the " + side
                    + " side, expected high, normal, low");
        }
        if ((side == LogicalSide.CLIENT) != e.clientSide()) {
            violation("a firing from a " + (e.clientSide() ? "client" : "server") + " level reached the "
                    + side + " side's consumers -- the other side's firing was not dropped");
        }

        // The previous firing's event is closed by now: reading its native event must throw.
        JoinAny previous = state.previousJoin.put(side, e);
        if (previous != null) {
            boolean threw = false;
            try {
                previous.readNativeNow();
            } catch (IllegalStateException expected) {
                threw = true;
            }
            if (!threw) violation("a closed event's native event was still readable on the " + side + " side");
        }

        // This consumer runs inside JoinAny's dispatch, one deep: a post from here is two deep.
        if (SatchelHealth.firstTime("request:" + side)) {
            RequestProbe request = new RequestProbe();
            Satchel.require().eventBus().post(request);
            if (!request.trail.equals(List.of("high", "normal", "low"))) {
                violation("a request event posted from inside a consumer reached its consumers as "
                        + request.trail + " on the " + side + " side, expected high, normal, low");
            }
            OUT.info("[SatchelHealth] Module-event probe: a request event of an unrelated class, posted from "
                    + "inside another consumer's dispatch, reached its listenForAll consumers in priority order "
                    + "on the " + side + " side");
        }

        int firings = state.joinFirings.merge(side, 1, Integer::sum);
        if (firings == 1) {
            logOnce("first:" + side, "an unscoped event reached its global consumers in priority order on the "
                    + side + " side, and no firing from the other side leaked in");
        }
        if (firings == 3) {
            int built = state.unwatchedBuilt.getOrDefault(side, 0);
            if (built > 1) {
                violation("an event nobody subscribes to was built " + built + " times on the " + side
                        + " side; expected once, to learn its class");
            }
            logOnce("cheap:" + side, "on the " + side + " side an event with no subscriber was built " + built
                    + " time(s) in 3 firings, and a resolver that answers empty bounced "
                    + state.joinBounced.getOrDefault(side, 0) + " time(s) without building");
        }
    }

    // =================================================================
    // The Trinket: scope-bound consumers, and the synthetic cancelable event
    // =================================================================

    /**
     * Held by each level host scope on both sides. Subscribes to the scoped events in {@code
     * onAttach}, never unsubscribes, and posts the synthetic event once.
     */
    public static final class EventProbeTrinket implements Ticking {

        private SatchelEventBus.Subscription joins;
        private SatchelEventBus.Subscription probes;
        private boolean posted;
        private long joinsSeen;

        @Override
        public void onAttach(ScopeInfo info) {
            joins = info.listenFor(JoinInLevel.class, e -> onJoin(info, e));
            probes = info.listenFor(ProbeSignal.class, e -> onProbe(info, e));
        }

        private void onJoin(ScopeInfo scopeInfo, JoinInLevel e) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            if (e.context() != info) {
                violation("scoped delivery reached " + info.debugName() + " for an event resolved to "
                        + e.context().debugName());
            }
            if (e.clientSide() != (info.jigInfo().side() == LogicalSide.CLIENT)) {
                violation("a scoped event from a " + (e.clientSide() ? "client" : "server")
                        + " level reached the " + info.jigInfo().side() + " side's scope " + info.debugName());
            }
            joinsSeen++;
            logOnce("scoped:" + info.jigInfo().side(), "a scoped event reached only its own level scope ("
                    + info.debugName() + ") on the " + info.jigInfo().side() + " side");
        }

        private void onProbe(ScopeInfo info, ProbeSignal e) {
            if (e.context() != info) {
                violation("scoped delivery of the synthetic event reached " + info.debugName()
                        + " though it was posted for " + e.context().debugName());
            }
            e.trail.add("scope");
        }

        @Override
        public void onTickAfter(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            if (posted) return;
            posted = true;

            SatchelEventBus bus = info.jigInfo().foundation().eventBus();
            for (boolean veto : new boolean[]{false, true}) {
                ProbeNative nativeEvent = new ProbeNative();
                ProbeSignal signal = new ProbeSignal(nativeEvent, info, veto);
                signal.bindCancelState(nativeEvent::isCanceled);
                try {
                    bus.post(signal);
                } finally {
                    signal.close();
                }
                if (!signal.verified) {
                    violation("the synthetic " + (veto ? "vetoed" : "plain") + " event never reached its last "
                            + "consumer on " + info.debugName() + ": " + signal.trail);
                }
            }
            logOnce("cancel:" + info.jigInfo().side(), "a synthetic cancelable event skipped the consumers "
                    + "that did not opt in, reached the one that did, and reached only its own scope, on the "
                    + info.jigInfo().side() + " side");
        }

        @Override
        public void onDetach(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            // Satchel dropped the scope's consumers before this, though this class never unsubscribed.
            if (joins.isActive() || probes.isActive()) {
                violation("a scope-bound consumer survived the unload of " + info.debugName());
            }
            logOnce("removed:" + info.jigInfo().side(), "scope-bound consumers were removed when " + info.debugName()
                    + " unloaded, though the Trinket never unsubscribed (saw " + joinsSeen + " scoped event(s))");
        }

        @Override
        public String describe() {
            return "joins=" + joinsSeen;
        }
    }
}
