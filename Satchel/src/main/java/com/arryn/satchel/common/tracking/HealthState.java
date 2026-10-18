package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickThrottler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Everything {@link SatchelHealth} holds for one running world. An integrated server starts and
 * stops repeatedly inside one JVM, so none of this can live in a static of its own: {@code
 * SatchelHealth} keeps one instance per world identity and drops it when that server stops, which
 * leaves nothing for the next server to inherit.
 *
 * <p>
 * Side-thread state: each side's thread reads and writes its own world's instance.
 */
final class HealthState {

    /**
     * Violation checks queued by a teardown handler, drained by a later server tick (see {@link
     * SatchelHealth#defer}).
     */
    final Deque<Runnable> pendingChecks = new ArrayDeque<>();

    /**
     * The canary this side knows of: the Overworld mob a server spawned, or, on a client, the one
     * the synced canary fixture names. Cleared with the world.
     */
    volatile UUID canaryUuid;

    /**
     * The interest probe's progress on this server; null until it starts. Server-thread only.
     */
    InterestHealthCheck.Run interestProbe;

    /**
     * Spaces out the canary's "is it still alive" check.
     */
    final TickThrottler canaryCheck = new TickThrottler(200, new SchedulerClock.AutoClock());
}
