package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything {@link SatchelHealth} holds for one running world. An integrated server starts and
 * stops repeatedly inside one JVM, so none of this can live in a static of its own: {@code
 * SatchelHealth} keeps one instance per world identity and drops it when that server stops, which
 * leaves nothing for the next server to inherit.
 *
 * <p>
 * Server-thread state, except {@link #interests}, which is read from the server thread by the
 * mob-death and mob-leave gates and written by commands on the same thread; it is concurrent only
 * because {@code MobJig}'s poll reads it as a live view.
 */
final class HealthState {

    /**
     * Violation checks queued by a teardown handler, drained by a later server tick (see {@link
     * SatchelHealth#defer}).
     */
    final Deque<Runnable> pendingChecks = new ArrayDeque<>();

    /**
     * Mobs under manual interest ({@link SatchelHealth#watch}), per level.
     */
    final Map<Level, Set<UUID>> interests = new ConcurrentHashMap<>();

    /**
     * The Overworld canary this server spawned, if it has.
     */
    volatile UUID canaryUuid;

    /**
     * Spaces out the canary's "is it still alive" check.
     */
    final TickThrottler canaryCheck = new TickThrottler(200, new SchedulerClock.AutoClock());
}
