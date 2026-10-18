package test.arryn.satchel.event;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.lifecycle.SatchelEventBus;
import com.arryn.satchel.common.lifecycle.SatchelEventBus.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bus's own rules (SAT_111): priority order, scoped dispatch merged into one order,
 * receiveCancelled, removal, the nesting-depth guard and the failure policy. Plain JUnit on a bus
 * with an injected failure policy, no Minecraft or Forge classes.
 */
class SatchelEventBusTest {

    /** A module event with an Integer context and a settable cancel flag. */
    static final class Hit extends ModuleSignal<String, Integer> {
        final AtomicBoolean cancelled = new AtomicBoolean();

        Hit(int context) {
            super("native", context);
            bindCancelState(cancelled::get);
        }

        String read() {
            return nativeEvent();
        }
    }

    /** An unscoped module event. */
    static final class Tick extends ModuleSignal<String, Void> {
        Tick() {
            super("native", null);
        }
    }

    private boolean development;
    private final List<String> logged = new ArrayList<>();
    private SatchelEventBus bus;

    @BeforeEach
    void setUp() {
        development = true;
        logged.clear();
        bus = new SatchelEventBus(() -> development, (msg, t) -> logged.add(msg));
    }

    private SatchelEventBus.Subscription sub(Object key, Priority p, boolean recv, String owner, List<String> into, String tag) {
        return bus.subscribe(Hit.class, key, p, recv, owner, h -> into.add(tag));
    }

    @Test
    void consumers_run_in_priority_order_and_subscription_order_within_one_priority() {
        List<String> order = new ArrayList<>();
        sub(null, Priority.LOW, false, "t", order, "low");
        sub(null, Priority.NORMAL, false, "t", order, "normal-1");
        sub(null, Priority.HIGH, false, "t", order, "high");
        sub(null, Priority.NORMAL, false, "t", order, "normal-2");

        bus.post(new Hit(1));

        assertEquals(List.of("high", "normal-1", "normal-2", "low"), order);
    }

    @Test
    void a_scoped_event_reaches_global_subscribers_and_its_own_context_only() {
        List<String> order = new ArrayList<>();
        sub(null, Priority.NORMAL, false, "t", order, "global");
        sub(1, Priority.NORMAL, false, "t", order, "ctx-1");
        sub(2, Priority.NORMAL, false, "t", order, "ctx-2");

        bus.post(new Hit(1));

        assertEquals(List.of("global", "ctx-1"), order);
    }

    @Test
    void global_and_scoped_subscribers_merge_into_one_priority_order() {
        List<String> order = new ArrayList<>();
        sub(null, Priority.LOW, false, "t", order, "global-low");
        sub(7, Priority.HIGH, false, "t", order, "scoped-high");
        sub(null, Priority.NORMAL, false, "t", order, "global-normal");
        sub(7, Priority.NORMAL, false, "t", order, "scoped-normal");
        sub(7, Priority.LOW, false, "t", order, "scoped-low");

        bus.post(new Hit(7));

        // Same priority: the earlier subscription runs first, whichever list it sits in.
        assertEquals(List.of("scoped-high", "global-normal", "scoped-normal", "global-low", "scoped-low"), order);
    }

    @Test
    void an_unscoped_event_reaches_only_global_subscribers() {
        AtomicInteger global = new AtomicInteger();
        AtomicInteger scoped = new AtomicInteger();
        bus.subscribe(Tick.class, null, Priority.NORMAL, false, "t", e -> global.incrementAndGet());
        bus.subscribe(Tick.class, 1, Priority.NORMAL, false, "t", e -> scoped.incrementAndGet());

        bus.post(new Tick());

        assertEquals(1, global.get());
        assertEquals(0, scoped.get());
    }

    @Test
    void a_cancelled_event_skips_a_consumer_that_did_not_opt_in() {
        List<String> order = new ArrayList<>();
        sub(null, Priority.HIGH, false, "t", order, "vetoes");
        bus.subscribe(Hit.class, null, Priority.HIGH, false, "t", h -> h.cancelled.set(true));
        sub(null, Priority.NORMAL, false, "t", order, "skipped");
        sub(null, Priority.NORMAL, true, "t", order, "tallies");

        Hit hit = new Hit(1);
        bus.post(hit);

        // The first HIGH consumer ran before the veto; the NORMAL one that didn't opt in is skipped.
        assertEquals(List.of("vetoes", "tallies"), order);
        assertTrue(hit.isCanceled());
    }

    @Test
    void unsubscribing_ends_delivery_and_a_scoped_key_is_released() {
        List<String> order = new ArrayList<>();
        SatchelEventBus.Subscription s = sub(5, Priority.NORMAL, false, "t", order, "scoped");
        assertTrue(bus.hasSubscribers(Hit.class, 5));

        s.unsubscribe();

        assertFalse(s.isActive());
        assertFalse(bus.hasSubscribers(Hit.class, 5));
        bus.post(new Hit(5));
        assertEquals(List.of(), order);
        s.unsubscribe();   // harmless twice
    }

    @Test
    void hasSubscribers_answers_for_global_and_scoped() {
        assertFalse(bus.hasSubscribers(Hit.class, 1));

        bus.subscribe(Hit.class, 1, Priority.NORMAL, false, "t", h -> {});
        assertTrue(bus.hasSubscribers(Hit.class, 1));
        assertFalse(bus.hasSubscribers(Hit.class, 2));
        assertFalse(bus.hasSubscribers(Hit.class, null));

        bus.subscribe(Hit.class, null, Priority.NORMAL, false, "t", h -> {});
        assertTrue(bus.hasSubscribers(Hit.class, 2));
        assertTrue(bus.hasSubscribers(Hit.class, null));
    }

    @Test
    void the_original_two_argument_subscribe_still_works() {
        List<String> order = new ArrayList<>();
        bus.subscribe(Hit.class, h -> order.add("legacy"));
        Hit cancelled = new Hit(1);
        cancelled.cancelled.set(true);

        bus.post(cancelled);

        // Legacy consumers see everything, cancelled or not.
        assertEquals(List.of("legacy"), order);
    }

    @Test
    void nesting_deeper_than_the_guard_throws_in_development() {
        bus.subscribe(Hit.class, null, Priority.NORMAL, false, "t", h -> bus.post(new Hit(1)));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bus.post(new Hit(1)));

        assertTrue(e.getMessage().contains("nested deeper than " + SatchelEventBus.MAX_DEPTH), e.getMessage());
    }

    @Test
    void runaway_recursion_in_release_is_logged_and_unwinds() {
        development = false;
        bus.subscribe(Hit.class, null, Priority.NORMAL, false, "t", h -> bus.post(new Hit(1)));

        assertDoesNotThrow(() -> bus.post(new Hit(1)));

        assertFalse(logged.isEmpty());
    }

    @Test
    void an_owned_consumer_that_throws_crashes_in_development() {
        List<String> order = new ArrayList<>();
        bus.subscribe(Hit.class, null, Priority.HIGH, false, "mod", h -> { throw new IllegalArgumentException("boom"); });
        sub(null, Priority.NORMAL, false, "t", order, "after");

        assertThrows(IllegalArgumentException.class, () -> bus.post(new Hit(1)));

        assertEquals(List.of(), order);
        assertTrue(logged.isEmpty());
    }

    @Test
    void an_owned_consumer_that_throws_is_logged_with_its_owner_and_skipped_in_release() {
        development = false;
        List<String> order = new ArrayList<>();
        bus.subscribe(Hit.class, null, Priority.HIGH, false, "frontiermode:ingress", h -> { throw new IllegalArgumentException("boom"); });
        sub(null, Priority.NORMAL, false, "t", order, "after");

        assertDoesNotThrow(() -> bus.post(new Hit(1)));

        assertEquals(List.of("after"), order);
        assertEquals(1, logged.size());
        assertTrue(logged.get(0).contains("frontiermode:ingress"), logged.get(0));
    }

    @Test
    void an_ownerless_consumer_that_throws_always_propagates() {
        development = false;
        bus.subscribe(Hit.class, h -> { throw new IllegalArgumentException("boom"); });

        assertThrows(IllegalArgumentException.class, () -> bus.post(new Hit(1)));
    }

    @Test
    void a_module_event_reads_its_native_event_only_during_dispatch() {
        List<Hit> kept = new ArrayList<>();
        bus.subscribe(Hit.class, null, Priority.NORMAL, false, "t", h -> {
            assertEquals("native", h.read());
            kept.add(h);
        });

        Hit hit = new Hit(3);
        bus.post(hit);
        hit.close();

        assertThrows(IllegalStateException.class, kept.get(0)::read);
        assertEquals(3, hit.context());   // snapshot state stays readable
    }

    @Test
    void cancel_state_survives_the_close() {
        Hit hit = new Hit(1);
        hit.cancelled.set(true);
        hit.close();

        assertTrue(hit.isCanceled());
        hit.cancelled.set(false);   // the native event changing afterward is no longer read
        assertTrue(hit.isCanceled());
    }
}
