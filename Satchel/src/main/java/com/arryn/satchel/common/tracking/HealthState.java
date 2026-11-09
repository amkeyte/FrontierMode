package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickThrottler;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraftforge.fml.LogicalSide;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Everything {@link SatchelHealth} holds for one running world. An integrated server starts and
 * stops repeatedly inside one JVM, so none of this can live in a static of its own: {@code
 * SatchelHealth} keeps one instance per world identity and drops it when that server stops, which
 * leaves nothing for the next server to inherit.
 *
 * <p>
 * An integrated server's client and server share one world identity, and so one instance: a field both
 * sides write is concurrent, and a key one side records names that side.
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

    /**
     * What a probe has already reported in this world, so it prints one line per side. An integrated
     * server's two sides share this state (one world identity), so a key names its side.
     */
    final Set<String> logged = ConcurrentHashMap.newKeySet();

    /** {@code LivingHurtEvent} firings whose resolver answered empty for everything (SAT_134 probe 4). */
    final AtomicLong hurtResolved = new AtomicLong();

    /** Events built by the resolver that always answers empty: must stay 0. */
    final AtomicLong hurtNeverBuilt = new AtomicLong();

    /** Events built, and delivered, for the one tagged entity: exactly 1 each, per hurt the probe makes. */
    final AtomicLong hurtTaggedBuilt = new AtomicLong();
    final AtomicLong hurtTaggedDelivered = new AtomicLong();

    /** {@code LivingHurtEvent} firings seen by the canary-event resolver (SAT_113). */
    final AtomicLong canaryHurtFired = new AtomicLong();

    /**
     * {@code CanaryHurt} events built, and delivered to the canary's own scope: exactly 1 each per hurt
     * the probe makes (SAT_113).
     */
    final AtomicLong canaryHurtBuilt = new AtomicLong();
    final AtomicLong canaryHurtDelivered = new AtomicLong();

    /**
     * The mobs the canary-event probe is about to kill, and what the unscoped consumer saw for each
     * (whether it carried the canary mark). Only these are recorded, so the map does not grow with
     * every death in the world.
     */
    final Set<UUID> deathWatch = ConcurrentHashMap.newKeySet();
    final Map<UUID, Boolean> deathSeen = new ConcurrentHashMap<>();

    /**
     * Module events delivered for a probe mob's own {@code EntityJoinLevelEvent}: must stay 0, since a
     * mob that has not joined gets no scope to resolve the event to (SAT_135).
     */
    final AtomicLong joinDelivered = new AtomicLong();

    /**
     * The module-event probe's tallies (SAT_113), one entry per side because an integrated server's two
     * sides share this state: the last unscoped event seen, the firings seen, the firings a resolver that
     * always answers empty bounced, and the events built for the one nobody subscribes to.
     */
    final Map<LogicalSide, ModuleEventHealthCheck.JoinAny> previousJoin = new ConcurrentHashMap<>();
    final Map<LogicalSide, Integer> joinFirings = new ConcurrentHashMap<>();
    final Map<LogicalSide, Integer> joinBounced = new ConcurrentHashMap<>();
    final Map<LogicalSide, Integer> unwatchedBuilt = new ConcurrentHashMap<>();

    /**
     * The UUIDs of the canary scopes attached on each side right now, so a second scope for the same
     * mob, built from inside the first one's unload, is caught as it attaches before the first has
     * detached (SAT_135). Per side, because an integrated server's two sides share this state. A
     * detach removes its UUID whatever the canary is by then, so a canary that was replaced leaves
     * nothing behind.
     */
    private final Map<LogicalSide, Set<UUID>> canaryScopes = new ConcurrentHashMap<>();

    Set<UUID> canaryScopes(LogicalSide side) {
        return canaryScopes.computeIfAbsent(side, k -> ConcurrentHashMap.newKeySet());
    }
}
