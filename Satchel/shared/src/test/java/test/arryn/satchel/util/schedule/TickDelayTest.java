package test.arryn.satchel.util.schedule;

import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickDelay;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors {@code TickThrottlerTest}'s plain-JUnit, no-mocking style. Covers the one real
 * behavioral divergence from {@link TickDelay}'s throttling sibling explicitly: no free first
 * pass -- see the corresponding test below.
 */
class TickDelayTest {

    /* ------------------------------------------------------------
     * Constructor validation
     * --------------------------------------------------------- */

    @Test
    void delay_must_be_positive_longSupplier() {
        LongSupplier clock = () -> 0L;

        assertThrows(
                IllegalArgumentException.class,
                () -> new TickDelay(0, clock)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TickDelay(-1, clock)
        );
    }

    @Test
    void delay_must_be_positive_clockSource() {
        SchedulerClock.NullClock clock = new SchedulerClock.NullClock();

        assertThrows(
                IllegalArgumentException.class,
                () -> new TickDelay(0, clock)
        );
    }

    @Test
    void null_clock_sources_are_rejected() {
        assertThrows(
                NullPointerException.class,
                () -> new TickDelay(5, (LongSupplier) null)
        );

        assertThrows(
                NullPointerException.class,
                () -> new TickDelay(5, (SchedulerClock) null)
        );
    }

    /* ------------------------------------------------------------
     * The one real divergence from TickThrottler: no free first pass
     * --------------------------------------------------------- */

    @Test
    void first_call_never_fires_immediately() {
        AtomicLong clock = new AtomicLong(0);
        TickDelay delay = new TickDelay(5, clock::get);

        // Unlike TickThrottler.allow(), a fresh TickDelay's first poll only seeds the
        // baseline -- firing here would defeat the entire point of a delay.
        assertFalse(delay.allow());
    }

    /* ------------------------------------------------------------
     * Basic allow / disallow / one-shot latch behavior
     * --------------------------------------------------------- */

    @Test
    void fires_once_delay_elapsed_then_never_again() {
        AtomicLong clock = new AtomicLong(0);
        TickDelay delay = new TickDelay(5, clock::get);

        assertFalse(delay.allow()); // baseline only

        clock.set(4);
        assertFalse(delay.allow()); // not yet

        clock.set(5);
        assertTrue(delay.allow()); // fires

        clock.set(6);
        assertFalse(delay.allow()); // never again

        clock.set(100);
        assertFalse(delay.allow()); // still never again
    }

    @Test
    void disAllow_is_inverse_of_allow() {
        AtomicLong clock = new AtomicLong(0);
        TickDelay delay = new TickDelay(3, clock::get);

        assertTrue(delay.disAllow()); // baseline only, allow() was false

        clock.set(1);
        assertTrue(delay.disAllow());

        clock.set(3);
        assertFalse(delay.disAllow()); // fires -> disAllow inverts to false

        clock.set(4);
        assertTrue(delay.disAllow()); // spent, allow() false forever -> disAllow true forever
    }

    /* ------------------------------------------------------------
     * A big elapsed jump still fires exactly once, not repeatedly
     * --------------------------------------------------------- */

    @Test
    void large_jump_past_delay_still_fires_only_once() {
        AtomicLong clock = new AtomicLong(0);
        TickDelay delay = new TickDelay(5, clock::get);

        assertFalse(delay.allow()); // baseline at 0

        clock.set(1000);
        assertTrue(delay.allow());  // fires, however far past the delay we jumped

        clock.set(2000);
        assertFalse(delay.allow()); // still spent
    }

    /* ------------------------------------------------------------
     * reset()
     * --------------------------------------------------------- */

    @Test
    void reset_rearms_and_requires_the_full_delay_again() {
        AtomicLong clock = new AtomicLong(0);
        TickDelay delay = new TickDelay(5, clock::get);

        assertFalse(delay.allow());
        clock.set(5);
        assertTrue(delay.allow());

        clock.set(6);
        assertFalse(delay.allow()); // spent

        delay.reset();

        // Reset restarts from scratch -- matching allow()'s own first-poll semantics, not an
        // immediate pass -- so the very next call is still false, same shape as a fresh
        // instance, not TickThrottler.reset()'s "next call is true" behavior.
        assertFalse(delay.allow());

        clock.set(11);
        assertTrue(delay.allow());
    }

    /* ------------------------------------------------------------
     * AutoClock behavior
     * --------------------------------------------------------- */

    @Test
    void autoClock_takes_delay_plus_one_polls_to_fire() {
        SchedulerClock.AutoClock clock = new SchedulerClock.AutoClock();
        TickDelay delay = new TickDelay(3, clock);

        assertFalse(delay.allow()); // poll 1: baseline only
        assertFalse(delay.allow()); // poll 2: elapsed 1
        assertFalse(delay.allow()); // poll 3: elapsed 2
        assertTrue(delay.allow());  // poll 4: elapsed 3 -- fires
        assertFalse(delay.allow()); // poll 5: spent
        assertFalse(delay.allow()); // poll 6: still spent
    }

    /* ------------------------------------------------------------
     * NullClock behavior -- never fires at all, unlike TickThrottler's
     * "allows once then never again" (which is really just its free first pass).
     * --------------------------------------------------------- */

    @Test
    void nullClock_never_fires() {
        SchedulerClock.NullClock clock = new SchedulerClock.NullClock();
        TickDelay delay = new TickDelay(1, clock);

        assertFalse(delay.allow());
        assertFalse(delay.allow());
        assertFalse(delay.allow());
    }

    /* ------------------------------------------------------------
     * Accessors
     * --------------------------------------------------------- */

    @Test
    void ticks_is_exposed() {
        TickDelay delay = new TickDelay(7, () -> 0L);

        assertEquals(7, delay.ticks());
    }
}
