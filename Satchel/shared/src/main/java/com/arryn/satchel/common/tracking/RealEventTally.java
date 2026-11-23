package com.arryn.satchel.common.tracking;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * What {@link RealEventHealthCheck} counts on threads that have no {@link HealthState} to count in: a
 * chunk-generation worker Satchel never bound, and the trades event the loader posts before the first
 * level (and so before any world identity) exists. A {@code HealthState} is found through the
 * world-identity token of the calling thread's side, which neither has.
 *
 * <p>
 * The one anchor in SatchelHealth besides its map of {@code HealthState}s. It is cleared when a server
 * stops, {@link SatchelHealth#onServerStopped()}, so a second integrated server in one JVM never
 * inherits the first's counts. The spawn counts are cleared at server start too ({@link
 * SatchelHealth#onServerAboutToStart()}), through {@link #resetForServerStart()}; the trades counts are
 * not, because a loader may post the trades event before the server's about-to-start event, and a
 * clear there would wipe the firing before the probe reads it. A server that ended without a stop
 * event (a crash) leaves its trades counts behind. Every member is thread-safe, since the worker
 * threads write while the server thread reads.
 */
final class RealEventTally {

    private RealEventTally() {}

    // ---- FinalizeSpawn (SAT_134 probe 1) ----

    /** Firings on a thread bound to the server, with its foundation ready: each one must be delivered. */
    private static final LongAdder SPAWN_EXPECTED = new LongAdder();
    private static final LongAdder SPAWN_DELIVERED = new LongAdder();
    /** Firings on a bound thread whose foundation was not the server's, or not ready: dropped. */
    private static final LongAdder SPAWN_NOT_READY = new LongAdder();
    /** Firings on a thread with no side bound, by thread name: dropped. */
    private static final Map<String, LongAdder> SPAWN_UNBOUND = new ConcurrentHashMap<>();

    // ---- VillagerTradesEvent (SAT_134 probe 6) ----

    private static final LongAdder TRADES_RAW = new LongAdder();
    private static final LongAdder TRADES_ON_BOUND_THREAD = new LongAdder();
    private static final LongAdder TRADES_DELIVERED = new LongAdder();

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /** Everything: for a server that has stopped. */
    static void reset() {
        resetForServerStart();
        TRADES_RAW.reset();
        TRADES_ON_BOUND_THREAD.reset();
        TRADES_DELIVERED.reset();
    }

    /** All but the trades counts, which a loader may already have filled before the server's about-to-start event. */
    static void resetForServerStart() {
        SPAWN_EXPECTED.reset();
        SPAWN_DELIVERED.reset();
        SPAWN_NOT_READY.reset();
        SPAWN_UNBOUND.clear();
        LOGGED.clear();
    }

    /** True the first time {@code key} is offered since the last reset. */
    static boolean firstTime(String key) {
        return LOGGED.add(key);
    }

    // ---- probe 1 ----

    static void spawnExpected() {
        SPAWN_EXPECTED.increment();
    }

    static void spawnDelivered() {
        SPAWN_DELIVERED.increment();
    }

    static void spawnNotReady() {
        SPAWN_NOT_READY.increment();
    }

    /** Counts a firing on an unbound thread; returns how many that thread has had, this one included. */
    static long spawnUnbound(String thread) {
        LongAdder adder = SPAWN_UNBOUND.computeIfAbsent(thread, k -> new LongAdder());
        adder.increment();
        return adder.sum();
    }

    static long spawnExpectedCount() {
        return SPAWN_EXPECTED.sum();
    }

    static long spawnDeliveredCount() {
        return SPAWN_DELIVERED.sum();
    }

    static long spawnNotReadyCount() {
        return SPAWN_NOT_READY.sum();
    }

    static long spawnUnboundCount() {
        long total = 0;
        for (LongAdder adder : SPAWN_UNBOUND.values()) total += adder.sum();
        return total;
    }

    // ---- probe 6 ----

    static void tradesRaw(boolean onBoundThread) {
        TRADES_RAW.increment();
        if (onBoundThread) TRADES_ON_BOUND_THREAD.increment();
    }

    static void tradesDelivered() {
        TRADES_DELIVERED.increment();
    }

    static long tradesRawCount() {
        return TRADES_RAW.sum();
    }

    static long tradesOnBoundThreadCount() {
        return TRADES_ON_BOUND_THREAD.sum();
    }

    static long tradesDeliveredCount() {
        return TRADES_DELIVERED.sum();
    }
}
