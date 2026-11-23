package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.LogicalSide;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;

/**
 * Real loader events, counted against a raw loader listener (SAT_134 probes 1 and 6) -- see {@link SatchelHealth}.
 * Each probe is a module event on an ingress, and beside it a plain listener on the loader's own bus that counts the
 * same firings, so the ingress is checked against what the loader really fired and not against itself.
 *
 * <ul>
 *   <li><b>A server ingress on a real event, live.</b> The finalize-spawn event, unscoped, one global consumer.
 *   Delivered equals raw on every bound thread. A firing on a thread Satchel has not bound is dropped and counted, and
 *   the first on each thread logs the thread's name and that it was not delivered: a spawn finalized at chunk
 *   generation is that case. A firing on a bound thread whose foundation is not ready is the other way a firing is
 *   dropped, and is counted apart;</li>
 *   <li><b>A pre-level event is not delivered.</b> The loader posts the trades event before the server's first level
 *   loads, so no side is bound and no foundation is ready. A probe ingress on it receives nothing, on the first server
 *   of a session and on a second in the same session. (On the first server the ingress is not even installed yet: a
 *   server foundation is booted by the first level load.) This documents the gap SAT_133 left open on purpose: a
 *   module takes the event by its own direct listener, not through the ingress. Where the loader posts the event
 *   differs by line: before the server's about-to-start event, or during it. A loader that posts it again while the
 *   server runs (a data reload) may do so on a bound thread, where the ingress delivers it; the probe reads before
 *   the first level, so it does not see that.</li>
 * </ul>
 *
 * <p>
 * The counts live in {@link RealEventTally}, not a {@link HealthState}: a chunk-generation worker and the
 * trades event a loader posts before the first level have no world identity to find a {@code HealthState} by.
 *
 * <p>
 * The check is shared. Each line's {@code RealEventIngress} implements the two event interfaces on its loader's
 * finalize-spawn and trades events, installs {@link RealEventHealthCheck#consumers()}, and registers the raw controls,
 * which call {@link RealEventHealthCheck#onRawFinalizeSpawn()} and {@link RealEventHealthCheck#onRawTrades()}. The
 * trades probe is read from shared code, not a line's listener: {@code ServerLifecycle.firstLevelLoad()} calls
 * {@link SatchelHealth#onServerFirstLevelLoad()} as a server's first level begins to load.
 */
final class RealEventHealthCheck {

    private RealEventHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** Contributes the tally Trinket. Each line registers the ingress and the raw controls. */
    static void register() {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                SpawnTallyTrinket.class, SpawnTallyTrinket::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Real event " + message);
    }

    // =================================================================
    // The module events, and the consumers each line's ingress installs
    // =================================================================

    /** Unscoped: a mob finalized, wherever. Built for every firing while it has a subscriber. */
    interface SpawnFinalized extends ModuleEvent<Void> {
    }

    /** Unscoped: a villager profession's trades loaded. */
    interface TradesLoaded extends ModuleEvent<Void> {
    }

    static GlobalConsumers consumers() {
        GlobalConsumers consumers = new GlobalConsumers();
        consumers.listenForAll(SpawnFinalized.class, e -> onSpawnDelivered()).receiveCancelled();
        consumers.listenForAll(TradesLoaded.class, e -> RealEventTally.tradesDelivered());
        return consumers;
    }

    // =================================================================
    // Probe 1: the finalize-spawn event
    // =================================================================

    /**
     * The raw control, called by the line's plain listener for every firing. Counts the firing as the guards would
     * judge it: a thread with no side bound, or a bound one whose foundation is not the server's or not ready, is
     * dropped; any other is expected to reach the consumer.
     */
    static void onRawFinalizeSpawn() {
        if (LogicalSideContext.peek() == null) {
            String thread = Thread.currentThread().getName();
            long n = RealEventTally.spawnUnbound(thread);
            if (n == 1) {
                OUT.info("[SatchelHealth] Real-event probe: FinalizeSpawn on thread '" + thread
                        + "', which has no side bound, was dropped (delivered=false)");
            }
            return;
        }

        LogicalFoundation bound = Satchel.peekFoundation();
        if (bound == null || bound.side() != LogicalSide.SERVER || !bound.isReady()) {
            RealEventTally.spawnNotReady();
            return;
        }
        RealEventTally.spawnExpected();
    }

    private static void onSpawnDelivered() {
        LogicalSide side = LogicalSideContext.peek();
        if (side != LogicalSide.SERVER) {
            violation("a FinalizeSpawn reached the server ingress's consumer on thread '"
                    + Thread.currentThread().getName() + "' bound to " + side);
        }
        RealEventTally.spawnDelivered();
    }

    /**
     * On the server's level host: where the counts are compared, between firings, since the raw listener and the
     * ingress's own run inside the same loader post.
     */
    public static final class SpawnTallyTrinket implements Ticking {

        private static final long REPORT_AFTER_TICKS = 2400;

        /** This scope's own tick count, since the foundation's number keeps counting across worlds. */
        private long ticks;

        @Override
        public void onTickAfter(ScopeInfo info) {
            LogicalSide side = ScopeInfoImpl.of(info).jigInfo().side();
            long expected = RealEventTally.spawnExpectedCount();
            long delivered = RealEventTally.spawnDeliveredCount();
            if (expected != delivered) {
                violation("FinalizeSpawn: " + expected + " firing(s) on bound, ready threads but " + delivered
                        + " delivered to the global consumer");
            }

            // One line per world: at the first delivery, or after two minutes if none has come, since a
            // quiet world may spawn nothing on a bound thread for a while.
            if (delivered > 0 || ++ticks >= REPORT_AFTER_TICKS) {
                SatchelHealth.logOnce("spawn:" + side, "[SatchelHealth] Real-event probe: "
                        + "FinalizeSpawn reached an unscoped global consumer on the " + side
                        + " side " + delivered + " time(s), equal to the raw firings on bound threads ("
                        + expected + "); " + RealEventTally.spawnUnboundCount() + " on unbound threads and "
                        + RealEventTally.spawnNotReadyCount() + " on bound threads not ready were dropped");
            }
        }
    }

    // =================================================================
    // Probe 6: the trades event before the first level
    // =================================================================

    /** The raw control, called by the line's plain listener for every trades firing. */
    static void onRawTrades() {
        RealEventTally.tradesRaw(LogicalSideContext.peek() != null);
    }

    /**
     * Called as a server's first level begins to load, before any level exists: by then the loader has posted the
     * trades event, in a listener of the server's about-to-start event or earlier. The trades counts are cleared when a
     * server stops, not when one starts ({@link RealEventTally}), so an early firing is still counted. Reports what
     * reached the ingress, once per server: a server loads a level for each dimension.
     */
    static void onServerFirstLevel() {
        if (!RealEventTally.firstTime("trades:read")) return;

        long raw = RealEventTally.tradesRawCount();
        long onBound = RealEventTally.tradesOnBoundThreadCount();
        long delivered = RealEventTally.tradesDeliveredCount();

        if (delivered != 0) {
            violation("the trades event reached the ingress " + delivered + " time(s) before the server's first "
                    + "level, where no level exists. SAT_133 keeps it out of the ingress: a module takes it by "
                    + "a direct listener.");
        }
        if (raw == 0) {
            OUT.warn("[SatchelHealth] Pre-level probe: the loader did not post the trades event before the server's "
                    + "first level, so the probe observed nothing (see SAT_133)");
            return;
        }
        OUT.info("[SatchelHealth] Pre-level probe: the trades event was posted " + raw + " time(s) before the "
                + "server's first level (" + onBound + " on a bound thread) and " + delivered
                + " reached the ingress, as expected: it stays out of the ingress (SAT_133)");
    }
}
