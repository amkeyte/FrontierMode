package test.arryn.satchel.event;

import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.ingress.ModuleEvent;
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
 * receiveCancelled, removal, the nesting-depth guard and failure; and a module event posted as the
 * module's interface, with global consumers declared in shared (SAT_188). Plain JUnit on a bus, no
 * Minecraft or Forge classes.
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

    /** A module's event interface, as written in shared for an event that runs on two lines. */
    interface Struck extends ModuleEvent<Integer> {
        /** A live read: the line's class reads it from the native event when asked. */
        String live();
    }

    /** One line's class for it, on that line's native event (a string here). */
    static final class LineStruck extends ModuleSignal<String, Integer> implements Struck {
        final AtomicBoolean cancelled = new AtomicBoolean();

        LineStruck(int context) {
            super("line native", context);
            bindCancelState(cancelled::get);
        }

        @Override
        public String live() {
            return nativeEvent();
        }
    }

    private SatchelEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new SatchelEventBus();
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
    void nesting_deeper_than_the_guard_throws() {
        bus.subscribe(Hit.class, null, Priority.NORMAL, false, "t", h -> bus.post(new Hit(1)));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bus.post(new Hit(1)));

        assertTrue(e.getMessage().contains("nested deeper than " + SatchelEventBus.MAX_DEPTH), e.getMessage());
    }

    @Test
    void an_owned_consumer_that_throws_crashes() {
        List<String> order = new ArrayList<>();
        bus.subscribe(Hit.class, null, Priority.HIGH, false, "mod", h -> { throw new IllegalArgumentException("boom"); });
        sub(null, Priority.NORMAL, false, "t", order, "after");

        assertThrows(IllegalArgumentException.class, () -> bus.post(new Hit(1)));

        assertEquals(List.of(), order);
    }

    @Test
    void an_ownerless_consumer_that_throws_always_propagates() {
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

    // =================================================================
    // A module event posted as the module's interface (SAT_188)
    // =================================================================

    @Test
    void an_event_posted_as_its_interface_reaches_the_interface_consumers_and_not_its_class() {
        List<String> order = new ArrayList<>();
        bus.subscribe(Struck.class, null, Priority.NORMAL, false, "t", e -> order.add("interface:" + e.live()));
        bus.subscribe(LineStruck.class, null, Priority.NORMAL, false, "t", e -> order.add("class"));

        bus.post(new LineStruck(1), Struck.class);
        assertEquals(List.of("interface:line native"), order);

        // Posted as its own class, as before, it reaches only the class's consumers.
        order.clear();
        bus.post(new LineStruck(1));
        assertEquals(List.of("class"), order);
    }

    @Test
    void scoped_dispatch_and_cancel_state_hold_under_the_declared_type() {
        List<String> order = new ArrayList<>();
        bus.subscribe(Struck.class, null, Priority.HIGH, false, "t", e -> ((LineStruck) e).cancelled.set(true));
        bus.subscribe(Struck.class, 4, Priority.NORMAL, false, "t", e -> order.add("skipped"));
        bus.subscribe(Struck.class, 4, Priority.NORMAL, true, "t", e -> order.add("ctx-4"));
        bus.subscribe(Struck.class, 5, Priority.NORMAL, true, "t", e -> order.add("ctx-5"));

        LineStruck struck = new LineStruck(4);
        bus.post(struck, Struck.class);

        assertEquals(List.of("ctx-4"), order);
        assertTrue(struck.isCanceled());
        assertTrue(bus.hasSubscribers(Struck.class, 5));
        assertFalse(bus.hasSubscribers(LineStruck.class, 4));
    }

    @Test
    void a_live_read_through_the_interface_throws_after_dispatch() {
        List<Struck> kept = new ArrayList<>();
        bus.subscribe(Struck.class, null, Priority.NORMAL, false, "t", kept::add);

        LineStruck struck = new LineStruck(2);
        bus.post(struck, Struck.class);
        struck.close();

        assertThrows(IllegalStateException.class, kept.get(0)::live);
        assertEquals(2, kept.get(0).context());   // the context stays readable
    }

    @Test
    void posting_as_a_type_the_event_is_not_is_a_programming_error() {
        assertThrows(IllegalArgumentException.class, () -> bus.post(new Tick(), Struck.class));
    }

    // =================================================================
    // Global consumers declared in shared (SAT_188)
    // =================================================================

    @Test
    void declared_consumers_install_on_a_bus_in_priority_order_with_their_options() {
        List<String> order = new ArrayList<>();
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(Struck.class, e -> order.add("normal"));
        declared.listenForAll(Struck.class, e -> order.add("low-tally")).priority(Priority.LOW).receiveCancelled();
        declared.listenForAll(Struck.class, e -> {
            order.add("high-veto");
            ((LineStruck) e).cancelled.set(true);
        }).priority(Priority.HIGH);

        declared.install(bus, "module");
        bus.post(new LineStruck(1), Struck.class);

        // The veto skips the NORMAL consumer, which did not opt in; the LOW one did.
        assertEquals(List.of("high-veto", "low-tally"), order);
        assertEquals(3, bus.listenerCount(Struck.class));
    }

    @Test
    void declared_consumers_install_on_each_bus_they_are_handed_to() {
        AtomicInteger heard = new AtomicInteger();
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(Struck.class, e -> heard.incrementAndGet());
        SatchelEventBus other = new SatchelEventBus();

        declared.install(bus, "module");
        declared.install(other, "module");
        bus.post(new LineStruck(1), Struck.class);
        other.post(new LineStruck(1), Struck.class);

        assertEquals(2, heard.get());
    }

    @Test
    void a_consumer_declared_after_install_is_a_programming_error() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.install(bus, "module");

        assertThrows(IllegalStateException.class, () -> declared.listenForAll(Struck.class, e -> {}));
    }
}
