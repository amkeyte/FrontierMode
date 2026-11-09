package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/**
 * Real Forge events, counted against a raw Forge listener (SAT_134 probes 1 and 6) -- see {@link
 * SatchelHealth}. Each probe is a module event on an ingress, and beside it a plain Forge listener
 * that counts the same firings, so the ingress is checked against what Forge really fired and not
 * against itself.
 *
 * <ul>
 *   <li><b>A server ingress on a real event, live.</b> {@code MobSpawnEvent.FinalizeSpawn}, unscoped,
 *   one global consumer. Delivered equals raw on every bound thread. A firing on a thread Satchel has
 *   not bound is dropped and counted, and the first on each thread logs the thread's name and that it
 *   was not delivered: a spawn finalized at chunk generation is that case. A firing on a bound thread
 *   whose foundation is not ready is the other way a firing is dropped, and is counted apart;</li>
 *   <li><b>A pre-level event is not delivered.</b> {@code VillagerTradesEvent} is posted from Forge's
 *   own {@code ServerAboutToStartEvent} listener, before the server's first level loads, so no side is
 *   bound and no foundation is ready. A probe ingress on it receives nothing, on the first server of a
 *   session and on a second in the same session. (On the first server the ingress is not even
 *   installed yet: a server foundation is booted by the first level load.) This documents the gap
 *   SAT_133 closes, and its expectation flips when that is built.</li>
 * </ul>
 *
 * <p>
 * The counts live in {@link RealEventTally}, not a {@link HealthState}: a chunk-generation worker and
 * the about-to-start event have no world identity to find a {@code HealthState} by.
 */
@PlatformBound(reason = "health-probe")
final class RealEventHealthCheck {

    private RealEventHealthCheck() {}

    private static final String MODULE = "satchel_health";

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:real_events");

    static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                SpawnTallyTrinket.class, SpawnTallyTrinket::new);

        // The raw controls. receiveCancelled, like the ingress's own listener below, so a listener
        // that cancels a firing cannot make one of the two count it and the other not.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, MobSpawnEvent.FinalizeSpawn.class,
                RealEventHealthCheck::onRawFinalizeSpawn);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, VillagerTradesEvent.class,
                RealEventHealthCheck::onRawTrades);
        // Last, so every listener of the event (Forge's own trades loader included) has run.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerAboutToStartEvent.class,
                RealEventHealthCheck::onAboutToStartDone);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Real event " + message);
    }

    // =================================================================
    // The module events
    // =================================================================

    /** Unscoped: a mob finalized, wherever. Built for every firing while it has a subscriber. */
    static final class SpawnFinalized extends ModuleSignal<MobSpawnEvent.FinalizeSpawn, Void> {
        SpawnFinalized(MobSpawnEvent.FinalizeSpawn e) {
            super(e, null);
        }
    }

    /** Unscoped: a villager profession's trades loaded. */
    static final class TradesLoaded extends ModuleSignal<VillagerTradesEvent, Void> {
        TradesLoaded(VillagerTradesEvent e) {
            super(e, null);
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);

        ingress.receiveCancelled().translate(MobSpawnEvent.FinalizeSpawn.class, SpawnFinalized::new);
        ingress.listenForAll(SpawnFinalized.class, RealEventHealthCheck::onSpawnDelivered).receiveCancelled();

        ingress.translate(VillagerTradesEvent.class, TradesLoaded::new);
        ingress.listenForAll(TradesLoaded.class, e -> RealEventTally.tradesDelivered());

        return ingress;
    }

    // =================================================================
    // Probe 1: FinalizeSpawn
    // =================================================================

    /**
     * The raw control. Counts the firing as the guards would judge it: a thread with no side bound,
     * or a bound one whose foundation is not the server's or not ready, is dropped; any other is
     * expected to reach the consumer.
     */
    private static void onRawFinalizeSpawn(MobSpawnEvent.FinalizeSpawn e) {
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

    private static void onSpawnDelivered(SpawnFinalized e) {
        LogicalSide side = LogicalSideContext.peek();
        if (side != LogicalSide.SERVER) {
            violation("a FinalizeSpawn reached the server ingress's consumer on thread '"
                    + Thread.currentThread().getName() + "' bound to " + side);
        }
        RealEventTally.spawnDelivered();
    }

    /**
     * On the server's level host: where the counts are compared, between firings, since the raw
     * listener and the ingress's own run inside the same Forge post.
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
    // Probe 6: VillagerTradesEvent at server start
    // =================================================================

    private static void onRawTrades(VillagerTradesEvent e) {
        RealEventTally.tradesRaw(LogicalSideContext.peek() != null);
    }

    /**
     * Runs after every other listener of {@code ServerAboutToStartEvent}, including Forge's loader
     * that posts the trades. Reports what reached the ingress.
     */
    private static void onAboutToStartDone(ServerAboutToStartEvent e) {
        long raw = RealEventTally.tradesRawCount();
        long onBound = RealEventTally.tradesOnBoundThreadCount();
        long delivered = RealEventTally.tradesDeliveredCount();

        if (delivered != 0) {
            violation("VillagerTradesEvent reached the ingress " + delivered + " time(s) during "
                    + "ServerAboutToStartEvent, before any level exists. SAT_133 is meant to change this: "
                    + "update the probe with it.");
        }
        if (raw == 0) {
            OUT.warn("[SatchelHealth] Pre-level probe: Forge did not post VillagerTradesEvent during "
                    + "ServerAboutToStartEvent, so the probe observed nothing (see SAT_133)");
            return;
        }
        OUT.info("[SatchelHealth] Pre-level probe: VillagerTradesEvent was posted " + raw + " time(s) during "
                + "ServerAboutToStartEvent (" + onBound + " on a bound thread) and " + delivered
                + " reached the ingress, as expected until SAT_133");
    }
}
