package test.arryn.satchel.util.schedule;

import com.arryn.satchel.common.util.out.Tracer;
import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class TickThrottlerTest {

    /* ------------------------------------------------------------
     * Constructor validation
     * --------------------------------------------------------- */

    @Test
    void interval_must_be_positive_longSupplier() {
        LongSupplier clock = () -> 0L;

        assertThrows(
                IllegalArgumentException.class,
                () -> new TickThrottler(0, clock)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TickThrottler(-1, clock)
        );
    }

    @Test
    void interval_must_be_positive_clockSource() {
        SchedulerClock.NullClock clock = new SchedulerClock.NullClock();

        assertThrows(
                IllegalArgumentException.class,
                () -> new TickThrottler(0, clock)
        );
    }

    @Test
    void null_clock_sources_are_rejected() {
        assertThrows(
                NullPointerException.class,
                () -> new TickThrottler(5, (LongSupplier) null)
        );

        assertThrows(
                NullPointerException.class,
                () -> new TickThrottler(5, (SchedulerClock) null)
        );
    }

    /* ------------------------------------------------------------
     * Basic allow / disallow behavior
     * --------------------------------------------------------- */

    @Test
    void first_call_is_always_allowed() {
        AtomicLong clock = new AtomicLong(0);
        TickThrottler throttler =
                new TickThrottler(5, clock::get);

        assertTrue(throttler.allow());
    }

    @Test
    void disallow_until_interval_elapsed() {
        AtomicLong clock = new AtomicLong(0);
        TickThrottler throttler =
                new TickThrottler(5, clock::get);

        assertTrue(throttler.allow());

        clock.set(4);
        assertFalse(throttler.allow());

        clock.set(5);
        assertTrue(throttler.allow());
    }

    @Test
    void disAllow_is_inverse_of_allow() {
        AtomicLong clock = new AtomicLong(0);
        TickThrottler throttler =
                new TickThrottler(3, clock::get);

        assertFalse(throttler.disAllow());

        clock.set(1);
        assertTrue(throttler.disAllow());

        clock.set(3);
        assertFalse(throttler.disAllow());
    }

    /* ------------------------------------------------------------
     * Drift-free stepping
     * --------------------------------------------------------- */

    @Test
    void advances_in_whole_intervals_without_drift() {
        AtomicLong clock = new AtomicLong(0);
        TickThrottler throttler =
                new TickThrottler(10, clock::get);

        assertTrue(throttler.allow()); // at 0

        clock.set(35);
        assertTrue(throttler.allow()); // advances by 30

        clock.set(39);
        assertFalse(throttler.allow());

        clock.set(40);
        assertTrue(throttler.allow());
    }

    /* ------------------------------------------------------------
     * reset()
     * --------------------------------------------------------- */

    @Test
    void reset_allows_immediate_next_call() {
        AtomicLong clock = new AtomicLong(0);
        TickThrottler throttler =
                new TickThrottler(10, clock::get);

        assertTrue(throttler.allow());

        clock.set(5);
        assertFalse(throttler.allow());

        throttler.reset();
        assertTrue(throttler.allow());
    }

    /* ------------------------------------------------------------
     * AutoClock behavior
     * --------------------------------------------------------- */

    @Test
    void autoClock_advances_elapsed_by_one_per_poll() {
        SchedulerClock.AutoClock clock =
                new SchedulerClock.AutoClock();

        TickThrottler throttler =
                new TickThrottler(3, clock);

        assertTrue(throttler.allow());   // tick 1
        assertFalse(throttler.allow());  // tick 2
        assertFalse(throttler.allow());  // tick 3
        assertTrue(throttler.allow());   // tick 4
        assertFalse(throttler.allow());  // tick 5
        assertFalse(throttler.allow());  // tick 6
        assertTrue(throttler.allow());   // tick 7
    }

    /* ------------------------------------------------------------
     * NullClock behavior
     * --------------------------------------------------------- */

    @Test
    void nullClock_allows_once_then_never_again() {
        SchedulerClock.NullClock clock =
                new SchedulerClock.NullClock();

        TickThrottler throttler =
                new TickThrottler(1, clock);

        assertTrue(throttler.allow());
        assertFalse(throttler.allow());
        assertFalse(throttler.allow());
    }

    /* ------------------------------------------------------------
     * Accessors
     * --------------------------------------------------------- */

    @Test
    void interval_is_exposed() {
        TickThrottler throttler =
                new TickThrottler(7, () -> 0L);

        assertEquals(7, throttler.interval());
    }

    @Test
    void tracer_never_constructs_throttler_with_zero_interval() {
        assertDoesNotThrow(() -> {
            Tracer tracer = new Tracer(/* minimal deps */);
            tracer.log("hello");
        });
    }


}
