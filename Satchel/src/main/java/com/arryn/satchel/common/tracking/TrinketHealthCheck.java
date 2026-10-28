package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.LifecycleState;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.trinket.TrinketSet;
import com.arryn.satchel.common.util.out.OUT;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trinket's slice of SatchelHealth (SAT_097) -- see {@link SatchelHealth}. Two plain test jigs of
 * their own, kept apart from every real jig so the check can't be satisfied by the code it guards.
 * Both are {@code LevelJig}s: the first holds a {@link Probe}, a {@link Gated} probe and one persisted
 * fixture; the second, registered after it and holding no bundle, holds an {@link EarlyProbe} and a
 * {@link LateProbe}.
 *
 * <p>
 * On a real scope the probes confirm, loudly (a failure is logged as a violation and the run goes on):
 * <ul>
 *   <li><b>attach</b> -- exactly once per scope load;</li>
 *   <li><b>both tick phases</b> -- strictly alternating {@code before}, {@code after}, and, while the
 *   probe bundle is ACTIVE, the fixture's own tick lands between them;</li>
 *   <li><b>the mid-tick rule</b> -- a Trinket attached during a tick first runs in the next one;</li>
 *   <li><b>order across two jigs</b> -- within one scope's tick the calls run {@code EarlyProbe},
 *   {@code Probe}, {@code LateProbe} in the before step and again in the after step, though the
 *   jig holding {@code Probe} registered first;</li>
 *   <li><b>the readiness gate</b> -- {@code Gated} is skipped and its lookup answers empty until its
 *   own {@code isReady} says true, then it ticks and the lookup answers;</li>
 *   <li><b>the dynamic Trinket</b> -- a subscriber added in the middle of a tick attaches at once and
 *   first runs in the next; one removed mid-tick detaches at once and never runs again; one left
 *   subscribed is detached by the host at unload;</li>
 *   <li><b>detach</b> -- only after an attach, and its fixture write is counted.</li>
 * </ul>
 * The one thing a single run can't see is whether the detach write was <i>saved</i>: that is read
 * back on the next load. The probe bundle's {@code detaches} count is logged at INFO when the scope
 * loads; a value above zero after a restart confirms a write made in {@code onDetach} reached disk.
 * The probes show in {@code /satchel trinkets}.
 */
final class TrinketHealthCheck {

    private TrinketHealthCheck() {}

    public static final BundleKey<TrinketProbeBundle> PROBE_BUNDLE =
            new BundleKey<>("satcheltracker:trinket_bundle", TrinketProbeBundle.class);

    public static final FixtureKey<TrinketProbeFixture> PROBE_FIXTURE =
            new FixtureKey<>("satcheltracker:trinket_fixture", TrinketProbeFixture.class);

    public static final JigKey<LevelJig> TRINKET_JIG =
            new JigKey<>("satcheltracker:trinket_jig", LevelJig.class);

    /** Registered after {@link #TRINKET_JIG}, so its Trinkets are the ones order must pull apart. */
    public static final JigKey<LevelJig> TRINKET_ORDER_JIG =
            new JigKey<>("satcheltracker:trinket_order_jig", LevelJig.class);

    /** The after-tick on which {@link Probe} opens {@link Gated}'s gate. */
    private static final long GATE_OPENS_AFTER = 3;

    static void register() {
        var fixture = new JigBundles.FixtureDecl<TrinketProbeFixture>(
                PROBE_FIXTURE, TrinketProbeFixture::new, JigPolicies.CreatePolicy.ALWAYS);

        var bundle = new JigBundles.BundleDecl<LevelScope, TrinketProbeBundle>(
                PROBE_BUNDLE,
                (LevelScope scope) -> new TrinketProbeBundle(scope, PROBE_BUNDLE),
                List.of(fixture));

        EventHandlers eventHandlers = EventHandlers.builder()
                .on(ScopeEvent.Loaded.class, TrinketHealthCheck::onLoaded)
                .build();

        LevelJigConfig config = new LevelJigConfig(TRINKET_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(bundle)));
        config.trinkets().declare(Probe.class, Probe::new).order(Order.NORMAL);
        config.trinkets().declare(Gated.class, Gated::new).order(Order.NORMAL);
        config.trinkets().declare(DynamicTrinket.class, DynamicTrinket::new);

        // Persistence only: the check is about attach/tick/detach and a saved detach write, not
        // networking. Server-side, LevelJigConfig's default.
        config.policies().capabilities(new JigPolicies.Capabilities(true, false, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);

        // The second jig holds Trinkets and nothing else: no bundle at all.
        LevelJigConfig orderConfig = new LevelJigConfig(TRINKET_ORDER_JIG);
        orderConfig.bundles().schema(new JigBundles.Schema<>(List.of()));
        orderConfig.trinkets().declare(EarlyProbe.class, EarlyProbe::new).order(Order.EARLY);
        orderConfig.trinkets().declare(LateProbe.class, LateProbe::new).order(Order.LATE);

        Satchel.registerJigConfig(orderConfig);
    }

    private static void onLoaded(ScopeEvent.Loaded event) {
        ScopeInfoImpl info = ScopeInfoImpl.of(event.info());
        if (!TRINKET_JIG.equals(info.jigInfo().key)) return;

        var bundle = info.jigInfo().jig.getOrCreate(info.scope(), PROBE_BUNDLE);
        var fixture = bundle.getOrCreateFixture(PROBE_FIXTURE, TrinketProbeFixture::new);

        // The probe's onAttach read the persisted count through ScopeInfo.getOrCreate, before this
        // handler ran; both reads are of the same hydrated state, so they must agree.
        Probe probe = info.jigInfo().coupler().trinkets(info).stream()
                .map(TrinketSet.Slot::trinket)
                .filter(Probe.class::isInstance)
                .map(Probe.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "[SatchelHealth] Trinket probe is missing on " + info.debugName()));
        if (probe.detachesAtAttach != fixture.detaches()) {
            violation("probe read " + probe.detachesAtAttach + " persisted detach write(s) in onAttach "
                    + "through ScopeInfo.getOrCreate but " + fixture.detaches() + " when the scope "
                    + "loaded, on " + info.debugName());
        }

        // Checkpoint: the saved-detach-write proof, read back across a restart.
        OUT.info("[SatchelHealth] Trinket probe loaded " + info.debugName()
                + ": detach writes persisted from earlier runs = " + fixture.detaches()
                + " (read in onAttach: " + probe.detachesAtAttach + ")");
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Trinket " + message);
    }

    private static Optional<TrinketProbeFixture> fixtureOf(ScopeInfo info) {
        return info.ask(PROBE_BUNDLE).flatMap(b -> b.get(PROBE_FIXTURE));
    }

    private static boolean bundleActive(ScopeInfo info) {
        return info.ask(PROBE_BUNDLE)
                .map(b -> b.lifeCycleState() == LifecycleState.ACTIVE)
                .orElse(false);
    }

    // =================================================================
    // Order across two jigs. Each probe reports its call here, with its rank in the expected order
    // of one scope's tick: the before step in order (EarlyProbe 0, Probe 1, LateProbe 2), then the
    // after step in the same order (3, 4, 5). Within a tick the ranks a scope reports must strictly
    // increase.
    // =================================================================

    private static final int RANK_EARLY_BEFORE = 0;
    private static final int RANK_PROBE_BEFORE = 1;
    private static final int RANK_LATE_BEFORE = 2;
    private static final int RANK_EARLY_AFTER = 3;
    private static final int RANK_PROBE_AFTER = 4;
    private static final int RANK_LATE_AFTER = 5;

    private static final class OrderLog {
        long tick = -1;
        int lastRank = -1;
        int calls;
        boolean proven;
    }

    /** Per level scope; dropped when {@link Probe} detaches. */
    private static final Map<UUID, OrderLog> ORDER_LOGS = new ConcurrentHashMap<>();

    private static void reportOrder(ScopeInfoImpl info, int rank, String name) {
        long tick = info.jigInfo().trinketTick().tickNumber();
        OrderLog log = ORDER_LOGS.computeIfAbsent(info.scopeId(), id -> new OrderLog());

        if (log.tick != tick) {
            log.tick = tick;
            log.lastRank = -1;
            log.calls = 0;
        }
        if (rank <= log.lastRank) {
            violation("order across two jigs broke on " + info.debugName() + " in tick " + tick + ": "
                    + name + " (rank " + rank + ") ran after a call of rank " + log.lastRank
                    + " -- expected EarlyProbe, Probe, LateProbe in the before step, then again in the "
                    + "after step");
        }
        log.lastRank = rank;
        log.calls++;

        if (rank == RANK_LATE_AFTER && log.calls == 6 && !log.proven) {
            log.proven = true;
            OUT.info("[SatchelHealth] Trinket order proven across two jigs on " + info.debugName()
                    + " (tick " + tick + "): EarlyProbe, Probe, LateProbe, in both steps");
        }
    }

    /**
     * The probe on the first jig: attach/tick/detach, the fixture bracket, the mid-tick rule, and the
     * driver of {@link Gated}'s gate. Public only because Satchel constructs it by type.
     */
    public static final class Probe implements Ticking {

        private SubscriberProbe transientSub;
        private SubscriberProbe keptSub;
        private long unsubscribedAtBefores;
        private long unsubscribedAtAfters;
        private long detachesAtAttach = -1;

        private int attaches;
        private long attachedInTick = -1;
        private long before;
        private long after;
        private long fixtureTicksAtBefore = -1;

        @Override
        public void onAttach(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            attaches++;
            if (attaches > 1) violation("probe attached " + attaches + " times to " + info.debugName());
            attachedInTick = info.jigInfo().trinketTick().tickNumber();

            // The persisted value, read here -- before the Loaded handler has created the bundle,
            // where ask() would be empty. onLoaded compares it with its own read.
            detachesAtAttach = info.getOrCreate(PROBE_BUNDLE)
                    .get(PROBE_FIXTURE)
                    .map(TrinketProbeFixture::detaches)
                    .orElseThrow(() -> new IllegalStateException(
                            "[SatchelHealth] Trinket probe fixture missing in onAttach on " + info.debugName()));
        }

        @Override
        public void onTickBefore(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            if (attaches != 1) violation("probe ticked before attach on " + info.debugName());
            long tick = info.jigInfo().trinketTick().tickNumber();
            if (tick <= attachedInTick) {
                violation("probe attached in tick " + attachedInTick + " ran in the same tick on "
                        + info.debugName() + " -- a Trinket attached during a tick first runs in the next");
            }
            if (before != after) violation("probe onTickBefore ran twice without an onTickAfter on "
                    + info.debugName());
            before++;
            reportOrder(info, RANK_PROBE_BEFORE, "Probe.onTickBefore");
            fixtureTicksAtBefore = bundleActive(info)
                    ? fixtureOf(info).map(TrinketProbeFixture::ticks).orElse(-1L)
                    : -1;
        }

        @Override
        public void onTickAfter(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            if (after + 1 != before) violation("probe onTickAfter ran without a matching onTickBefore on "
                    + info.debugName());
            after++;
            reportOrder(info, RANK_PROBE_AFTER, "Probe.onTickAfter");
            if (fixtureTicksAtBefore >= 0 && bundleActive(info)) {
                long now = fixtureOf(info).map(TrinketProbeFixture::ticks).orElse(-1L);
                if (now != fixtureTicksAtBefore + 1) {
                    violation("probe's tick phases did not bracket the fixtures' tick on "
                            + info.debugName() + ": fixture ticks " + fixtureTicksAtBefore
                            + " before, " + now + " after (expected exactly one in between)");
                }
            }
            checkGate(info);
            driveSubscribers(info);
        }

        /**
         * The dynamic Trinket's subscribers, driven from the middle of a tick. After tick 1 two are
         * subscribed: each must attach at once and first run in tick 2. After tick 4 one is
         * unsubscribed: it must detach at once and never run again, though tick 4's after step has
         * not reached it yet. The other stays until the scope unloads and is detached by the host.
         */
        private void driveSubscribers(ScopeInfoImpl info) {
            DynamicTrinket host = info.jigInfo().coupler().trinket(info, DynamicTrinket.class)
                    .orElseThrow(() -> new IllegalStateException(
                            "[SatchelHealth] Trinket dynamic host is missing on " + info.debugName()));

            if (after == 1) {
                // After Probe in the after step, so the unsubscribe below lands before its call.
                transientSub = new SubscriberProbe("transient");
                host.subscribe(transientSub, Order.LATE + 1);
                keptSub = new SubscriberProbe("kept");
                host.subscribe(keptSub, Order.EARLY - 1);

                for (SubscriberProbe sub : List.of(transientSub, keptSub)) {
                    if (sub.attaches != 1) violation("subscriber " + sub.name + " was not attached at "
                            + "once by subscribe on " + info.debugName());
                    if (sub.befores != 0) violation("subscriber " + sub.name + " ran in the tick it "
                            + "was subscribed in on " + info.debugName());
                }
                if (host.subscribers(SubscriberProbe.class).size() != 2) {
                    violation("dynamic host lookup did not find both subscribers on " + info.debugName());
                }
            }

            if (after == 4) {
                if (transientSub.befores < 2 || keptSub.befores < 2) {
                    violation("subscribers did not tick (transient " + transientSub.befores + ", kept "
                            + keptSub.befores + ") by tick 4 on " + info.debugName());
                }
                if (!host.unsubscribe(transientSub)) violation("unsubscribe did not find the subscriber");
                if (transientSub.detaches != 1) {
                    violation("unsubscribe did not detach at once on " + info.debugName());
                }
                if (host.subscribers(SubscriberProbe.class).size() != 1) {
                    violation("dynamic host lookup still finds the unsubscribed one on " + info.debugName());
                }
                unsubscribedAtBefores = transientSub.befores;
                unsubscribedAtAfters = transientSub.afters;
            }

            if (after > 4 && (transientSub.befores != unsubscribedAtBefores
                    || transientSub.afters != unsubscribedAtAfters)) {
                violation("subscriber ran after it was unsubscribed on " + info.debugName());
            }
        }

        /**
         * The readiness gate: {@link Gated} answers a lookup and ticks only once open. Opens it after
         * {@link #GATE_OPENS_AFTER} ticks, so the closed and the open case are both seen.
         */
        private void checkGate(ScopeInfoImpl info) {
            Gated gated = info.jigInfo().coupler().trinkets(info).stream()
                    .map(TrinketSet.Slot::trinket)
                    .filter(Gated.class::isInstance)
                    .map(Gated.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "[SatchelHealth] Trinket gated probe is missing on " + info.debugName()));

            boolean lookupAnswers = info.jigInfo().coupler().trinket(info, Gated.class).isPresent();
            if (lookupAnswers != gated.open) {
                violation("gated probe lookup answered " + (lookupAnswers ? "present" : "empty")
                        + " while its isReady is " + gated.open + " on " + info.debugName());
            }
            if (after > GATE_OPENS_AFTER && gated.befores != after - GATE_OPENS_AFTER) {
                violation("gated probe ticked " + gated.befores + " time(s) in the " + (after - GATE_OPENS_AFTER)
                        + " tick(s) since it opened on " + info.debugName());
            }
            if (after == GATE_OPENS_AFTER) {
                gated.open = true;
                OUT.info("[SatchelHealth] Trinket gated probe opened on " + info.debugName()
                        + " after " + after + " tick(s) closed without a tick or a lookup answer");
            }
        }

        @Override
        public void onDetach(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            if (attaches != 1) violation("probe detached without a prior attach on " + info.debugName());
            ORDER_LOGS.remove(info.scopeId());
            // The write under test: made before the engine unloads the bundle, saved by its
            // terminal flush, read back as TrinketProbeFixture#detaches on the next load.
            fixtureOf(info).ifPresent(TrinketProbeFixture::countDetach);
            OUT.info("[SatchelHealth] Trinket probe detached from " + info.debugName()
                    + " after " + after + " tick(s)");
        }

        @Override
        public String describe() {
            return "attaches=" + attaches + " tickBefore=" + before + " tickAfter=" + after;
        }
    }

    /**
     * A Trinket that is not ready until {@link Probe} opens it: while closed it must never tick and
     * its lookup must answer empty. Public only because Satchel constructs it by type.
     */
    public static final class Gated implements Ticking {

        private boolean open;
        private long befores;
        private long afters;

        @Override
        public boolean isReady(ScopeInfo info) {
            return open;
        }

        @Override
        public void onTickBefore(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            if (!open) violation("gated probe ticked (before) while not ready on " + info.debugName());
            befores++;
        }

        @Override
        public void onTickAfter(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            if (!open) violation("gated probe ticked (after) while not ready on " + info.debugName());
            afters++;
        }

        @Override
        public String describe() {
            return "open=" + open + " tickBefore=" + befores + " tickAfter=" + afters;
        }
    }

    /**
     * A subscriber of the dynamic Trinket on the first jig: counts its own attach, detach and tick
     * phases, and throws if it is called when it should not be -- ticked before it attached, in the
     * tick it was subscribed in, or after it detached.
     */
    static final class SubscriberProbe implements Ticking {

        final String name;
        int attaches;
        int detaches;
        long befores;
        long afters;
        private long attachedInTick = -1;

        SubscriberProbe(String name) {
            this.name = name;
        }

        @Override
        public void onAttach(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            attaches++;
            if (attaches > 1) violation("subscriber " + name + " attached " + attaches + " times to "
                    + info.debugName());
            attachedInTick = info.jigInfo().trinketTick().tickNumber();
        }

        private void checkLive(ScopeInfoImpl info, String call) {
            if (attaches != 1 || detaches != 0) {
                violation("subscriber " + name + " got " + call + " while not attached on "
                        + info.debugName());
            }
            if (info.jigInfo().trinketTick().tickNumber() <= attachedInTick) {
                violation("subscriber " + name + " got " + call + " in the tick it was subscribed in on "
                        + info.debugName() + " -- it first runs in the next");
            }
        }

        @Override
        public void onTickBefore(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            checkLive(info, "onTickBefore");
            if (befores != afters) violation("subscriber " + name + " got onTickBefore twice without an "
                    + "onTickAfter on " + info.debugName());
            befores++;
        }

        @Override
        public void onTickAfter(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            checkLive(info, "onTickAfter");
            if (afters + 1 != befores) violation("subscriber " + name + " got onTickAfter without a "
                    + "matching onTickBefore on " + info.debugName());
            afters++;
        }

        @Override
        public void onDetach(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            if (attaches != 1 || detaches != 0) {
                violation("subscriber " + name + " detached " + (detaches == 0 ? "without an attach" : "twice")
                        + " on " + info.debugName());
            }
            detaches++;
            OUT.info("[SatchelHealth] Trinket subscriber " + name + " detached from " + info.debugName()
                    + " after " + afters + " tick(s)");
        }

        @Override
        public String describe() {
            return name + " tickBefore=" + befores + " tickAfter=" + afters;
        }
    }

    /**
     * Declared {@link Order#EARLY} on the second jig: runs ahead of {@link Probe} though that jig
     * registered first. Public only because Satchel constructs it by type.
     */
    public static final class EarlyProbe implements Ticking {

        private long ticks;

        @Override
        public void onTickBefore(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            reportOrder(info, RANK_EARLY_BEFORE, "EarlyProbe.onTickBefore");
            ticks++;
        }

        @Override
        public void onTickAfter(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            reportOrder(info, RANK_EARLY_AFTER, "EarlyProbe.onTickAfter");
        }

        @Override
        public String describe() {
            return "ticks=" + ticks;
        }
    }

    /**
     * Declared {@link Order#LATE} on the second jig: runs after {@link Probe}. Public only because
     * Satchel constructs it by type.
     */
    public static final class LateProbe implements Ticking {

        private long ticks;

        @Override
        public void onTickBefore(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            reportOrder(info, RANK_LATE_BEFORE, "LateProbe.onTickBefore");
            ticks++;
        }

        @Override
        public void onTickAfter(ScopeInfo scopeInfo) {
            ScopeInfoImpl info = ScopeInfoImpl.of(scopeInfo);
            reportOrder(info, RANK_LATE_AFTER, "LateProbe.onTickAfter");
        }

        @Override
        public String describe() {
            return "ticks=" + ticks;
        }
    }
}
