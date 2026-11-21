package com.arryn.satchel.common.util.schedule;

import java.util.function.LongSupplier;

/**
 * A monotonic "elapsed" counter, queried on demand -- what {@link TickThrottler} and
 * {@link TickDelay} both gate against. Implementations decide what a unit is: real game
 * ticks, wall time, number of times this clock has been polled ({@link AutoClock}), anything
 * monotonic. Moved out from under {@code TickThrottler}'s own namespace (SAT_070) once
 * {@link TickDelay}, an unrelated consumer, needed the same clock abstraction -- named for the
 * role, not for either specific gate built on top of it.
 */
public interface SchedulerClock {
    /**
     * The elapsed number of whatever unit the implementation counts.
     * Examples would be time, number of ticks, number of calls to elapsedSupplier.
     */
    long elapsed();

    LongSupplier elapsedSupplier();

    /**
     * A clock that never advances -- {@link #elapsed()} always returns 0.
     */
    class NullClock implements SchedulerClock {
        @Override
        public long elapsed() {
            return 0;
        }

        private long getZero() {
            return 0;
        }

        @Override
        public LongSupplier elapsedSupplier() {
            return this::getZero;
        }
    }

    /**
     * A {@link SchedulerClock} that can be manually advanced by whatever drives it.
     */
    interface TickableClockSource extends SchedulerClock {
        void tick();
    }

    /**
     * Advances by exactly one every time it's polled by its owning gate -- ticks every time
     * the throttler/delay is polled, no external clock needed.
     */
    class AutoClock implements TickableClockSource {
        private long elapsed = 0L;

        @Override
        public long elapsed() {
            return elapsed;
        }

        @Override
        public LongSupplier elapsedSupplier() {
            return this::elapsed;
        }

        @Override
        public void tick() {
            elapsed++;
        }
    }
}
