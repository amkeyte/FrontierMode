package com.arryn.satchel.common.util.throttle;

import java.util.function.LongSupplier;

public interface ThrottleClockSource {
    /**
     * The elapsed number of whatever unit the implementation counts.
     * Examples would be time, number of ticks, number of calls to elapsedSupplier.
     */
    long elapsed();
    LongSupplier elapsedSupplier();
}
