package com.arryn.satchel.common.util.throttle;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * elapsed count based throttler driven by an injected clock source.
 * provides gating based on interval using a latched gate (allowed >= interval),
 * therefore throttling on a minimum time elapsed, not instant (allowed = interval)<br><br>
 *
 * clock source is queried on demand, so callers do not need
 * to manually clock or track counting etc.<br><br>
 *
 * generally used for log calls on render or ticking threads, but any
 * source capable of tracking elapsed changes from one state to another
 * are valid. examples are actual time comparisons, number of times
 * the throttler is polled, number of passes through a loop.
 */
public final class TickThrottler {
    /**
     *
     */
    public static class NullClock implements ThrottleClockSource {
        @Override
        public long elapsed() {
            return 0;
        }
        private long getZero() {return 0;}

        @Override
        public LongSupplier elapsedSupplier() {
            return this::getZero;
        }
    }
    public interface TickableClockSource extends ThrottleClockSource {
        void tick();
    }

    /**
     * ticks every time the throttler is polled.
     */
    public static class AutoClock implements TickableClockSource {
        private long elapsed = 0L;

        @Override
        public long elapsed() {
            return elapsed;
        }

        @Override
        public LongSupplier elapsedSupplier() {
            return this::elapsed; // <-- FIX
        }

        @Override
        public void tick(){
            elapsed++;
        }
    }

    private final long interval;
    private final LongSupplier timeSource;
    private  final ThrottleClockSource clockSource;

    private long lastTick = Long.MIN_VALUE;


    /**
     * @param intervalTicks minimum ticks between allows
     * @param timeSource    supplier of current game time
     */
    public TickThrottler(long intervalTicks, LongSupplier timeSource) {
        Objects.requireNonNull(timeSource,"timeSource");

        if (intervalTicks <= 0) {
            throw new IllegalArgumentException("intervalTicks must be > 0");
        }
        this.interval = intervalTicks;
        this.clockSource = null;
        this.timeSource = timeSource;
    }
   /**
     * @param intervalTicks minimum ticks between allows
     */
    public TickThrottler(long intervalTicks, ThrottleClockSource clockSource) {
        Objects.requireNonNull(clockSource,"clockSource");

        if (intervalTicks <= 0) {
            throw new IllegalArgumentException("intervalTicks must be > 0");
        }
        this.interval = intervalTicks;
        this.clockSource = clockSource;
        this.timeSource = clockSource.elapsedSupplier();
    }
    /**
     * Returns true when (currentElapsed - lastAllowedElapsed) >= interval.
     * The clock source defines what "elapsed" means.
     */
    public boolean allow() {

        long thisTick = timeSource.getAsLong();

        if(clockSource!= null
            && clockSource instanceof AutoClock tickable){
            tickable.tick();
        }


        if (lastTick == Long.MIN_VALUE) {
            lastTick = thisTick;
            //hitCount++;
            return true;
        }

        long elapsed = thisTick - lastTick;
        if (elapsed < interval) {
            return false;
        }

        // Advance in whole intervals to avoid drift
        long steps = elapsed / interval;
        lastTick += steps * interval;
        //hitCount++;
        return true;
    }

    public boolean disAllow(){
        return !allow();
    }

    /**
     * Forces the next call to allow immediately.
     */
    public void reset() {
        lastTick = Long.MIN_VALUE;
        //hitCount = 0;
    }



    public long interval() {
        return interval;
    }
}
