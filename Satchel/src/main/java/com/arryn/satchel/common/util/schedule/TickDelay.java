package com.arryn.satchel.common.util.schedule;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * SAT_070: {@link TickThrottler}'s one-shot sibling -- same construction and polling shape
 * (inject a clock, poll a boolean each tick), except {@link #allow()} fires at most once. Once
 * it has returned {@code true}, every later call returns {@code false} until {@link #reset()}
 * re-arms it.
 *
 * <p>
 * Motivating case: a caller wants "run this once, N ticks from now" rather than a recurring
 * cadence -- e.g. delaying a {@code SatchelBundle.forceWriteToDisk()} call a few ticks after a
 * significant event, so every sibling bundle mutated in the same tick has already had its own
 * {@code EXECUTION_PULSE} refresh run by the time the delayed call actually fires.
 *
 * <p>
 * <b>One real behavioral difference from {@link TickThrottler}, despite the shared shape:</b> a
 * fresh {@code TickThrottler} allows its very first call immediately -- there's nothing to
 * throttle against yet. A fresh {@code TickDelay} does the opposite on purpose: the first call
 * only seeds the baseline and always returns {@code false}, since firing on the first poll would
 * defeat the entire point of a delay. Concretely, under an {@link SchedulerClock.AutoClock},
 * {@code new TickDelay(N, clock)} takes exactly {@code N + 1} polls to fire -- the same shape as
 * a {@code TickThrottler} needing {@code interval + 1} polls to re-arm after its own free first
 * pass, just anchored differently (throttler's poll 1 is a pass; delay's poll 1 is baseline-only).
 */
public final class TickDelay {

    private final long ticks;
    private final LongSupplier timeSource;
    private final SchedulerClock clockSource;

    private long lastTick = Long.MIN_VALUE;
    private boolean fired;

    /**
     * @param delayTicks how many ticks must elapse before {@link #allow()} first returns true
     * @param timeSource supplier of current game time
     */
    public TickDelay(long delayTicks, LongSupplier timeSource) {
        Objects.requireNonNull(timeSource, "timeSource");

        if (delayTicks <= 0) {
            throw new IllegalArgumentException("delayTicks must be > 0");
        }
        this.ticks = delayTicks;
        this.clockSource = null;
        this.timeSource = timeSource;
    }

    /**
     * @param delayTicks how many ticks must elapse before {@link #allow()} first returns true
     */
    public TickDelay(long delayTicks, SchedulerClock clockSource) {
        Objects.requireNonNull(clockSource, "clockSource");

        if (delayTicks <= 0) {
            throw new IllegalArgumentException("delayTicks must be > 0");
        }
        this.ticks = delayTicks;
        this.clockSource = clockSource;
        this.timeSource = clockSource.elapsedSupplier();
    }

    /**
     * Returns {@code true} the first time (currentElapsed - baselineElapsed) >= ticks, and
     * {@code false} on every call before and after that -- once fired, this never fires again
     * on its own. The clock source defines what "elapsed" means, same as {@link TickThrottler}.
     */
    public boolean allow() {
        if (fired) {
            return false;
        }

        long thisTick = timeSource.getAsLong();

        if (clockSource instanceof SchedulerClock.AutoClock tickable) {
            tickable.tick();
        }

        if (lastTick == Long.MIN_VALUE) {
            // Baseline only -- deliberately does not fire on the first poll, unlike
            // TickThrottler's own first-call-always-allowed rule. See class doc.
            lastTick = thisTick;
        }

        long elapsed = thisTick - lastTick;
        if (elapsed < ticks) {
            return false;
        }

        fired = true;
        return true;
    }

    public boolean disAllow() {
        return !allow();
    }

    /**
     * Re-arms this delay -- clears the fired latch and the baseline, so the next call to
     * {@link #allow()} starts counting from scratch (not an immediate pass, matching
     * {@link #allow()}'s own first-poll behavior). Also the mechanism for making this delay
     * fire again after it's already fired once.
     */
    public void reset() {
        fired = false;
        lastTick = Long.MIN_VALUE;
    }

    public long ticks() {
        return ticks;
    }
}
