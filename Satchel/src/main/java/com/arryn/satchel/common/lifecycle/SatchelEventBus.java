package com.arryn.satchel.common.lifecycle;

import com.arryn.satchel.common.ingress.ModuleSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Synchronous event bus for Satchel's events: {@code ScopeEvent}, {@code BundleEvent}, {@code
 * SatchelEvent} and the module events ({@link ModuleSignal}) modules post through their ingress.
 *
 * <p>
 * One per foundation, so one per side. Dispatch is synchronous, depth-first and by exact event class,
 * like Forge's. If an event is posted here, all invariants have already been enforced: the bus does
 * not filter, infer or check lifecycle.
 *
 * <ul>
 *   <li><b>Priority.</b> A subscription has a {@link Priority}; consumers of an event run in priority
 *   order, and in subscription order within one priority.</li>
 *   <li><b>{@code receiveCancelled}.</b> A cancelled {@link ModuleSignal} is skipped for a consumer
 *   that did not opt in. (Other events carry no cancel state; every consumer gets them.)</li>
 *   <li><b>Scoped dispatch.</b> A subscription may be filed under a context key. A module event is
 *   delivered to the global subscribers plus those filed under its context's key, merged into one
 *   priority order. The resolver already found the context, so delivery is a map lookup: many scopes
 *   can subscribe to one event type without every handler running and rejecting. Only module events
 *   are delivered by context.</li>
 *   <li><b>A nesting-depth guard</b> ({@value #MAX_DEPTH}) so a consumer that triggers its own event
 *   recursively fails instead of overflowing the stack.</li>
 *   <li><b>Failure.</b> A consumer that throws propagates out of {@link #post}, in every build, and
 *   the dispatch stops. The bus catches nothing.</li>
 * </ul>
 */
public final class SatchelEventBus {

    /**
     * When a consumer runs among the consumers of one event: {@code HIGH} first.
     */
    public enum Priority {
        HIGH, NORMAL, LOW
    }

    /**
     * A handle on one subscription, to end it sooner than its owner would.
     */
    public interface Subscription {
        void unsubscribe();

        boolean isActive();
    }

    /**
     * How deep dispatches may nest before the bus throws.
     */
    public static final int MAX_DEPTH = 32;

    private static final class Sub implements Subscription {

        final SatchelEventBus bus;
        final Class<?> eventType;
        final Object contextKey;
        final Object consumer;
        final Priority priority;
        final boolean receiveCancelled;
        final String owner;
        final long sequence;
        volatile boolean active = true;

        Sub(SatchelEventBus bus, Class<?> eventType, Object contextKey, Object consumer,
            Priority priority, boolean receiveCancelled, String owner, long sequence) {
            this.bus = bus;
            this.eventType = eventType;
            this.contextKey = contextKey;
            this.consumer = consumer;
            this.priority = priority;
            this.receiveCancelled = receiveCancelled;
            this.owner = owner;
            this.sequence = sequence;
        }

        @Override
        public void unsubscribe() {
            bus.remove(this);
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    private static final class Subs {
        final CopyOnWriteArrayList<Sub> global = new CopyOnWriteArrayList<>();
        final ConcurrentHashMap<Object, CopyOnWriteArrayList<Sub>> scoped = new ConcurrentHashMap<>();
    }

    private final ConcurrentHashMap<Class<?>, Subs> byType = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    private long nextSequence;
    private int depth;

    /* =============================================================
     * Subscription
     * ========================================================== */

    /**
     * Subscribe to a specific event type, globally, at {@link Priority#NORMAL}, receiving cancelled
     * events, with no owner: the original subscription, kept for {@code ScopeEvent} and the other
     * Satchel events.
     */
    public <E> void subscribe(
            Class<E> eventType,
            Consumer<? super E> listener
    ) {
        subscribe(eventType, null, Priority.NORMAL, true, null, listener);
    }

    /**
     * Subscribes {@code consumer} to {@code eventType}.
     *
     * @param contextKey       null for a global subscription, which hears every firing; otherwise the
     *                         key (see {@link ContextKeyed}) of the one context it hears
     * @param receiveCancelled whether a cancelled {@link ModuleSignal} is delivered to it
     * @param owner            who owns the subscription (an ingress or a scope), or null for none
     * @return a handle to end the subscription
     */
    public <E> Subscription subscribe(
            Class<E> eventType,
            Object contextKey,
            Priority priority,
            boolean receiveCancelled,
            String owner,
            Consumer<? super E> consumer
    ) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(consumer, "consumer");

        synchronized (lock) {
            Sub sub = new Sub(this, eventType, contextKey, consumer, priority, receiveCancelled, owner,
                    nextSequence++);
            Subs subs = byType.computeIfAbsent(eventType, k -> new Subs());
            CopyOnWriteArrayList<Sub> list = contextKey == null
                    ? subs.global
                    : subs.scoped.computeIfAbsent(contextKey, k -> new CopyOnWriteArrayList<>());
            list.add(insertionIndex(list, sub), sub);
            return sub;
        }
    }

    /**
     * Unsubscribe a listener subscribed with {@link #subscribe(Class, Consumer)}.
     */
    public <E> void unsubscribe(
            Class<E> eventType,
            Consumer<? super E> listener
    ) {
        Subs subs = byType.get(eventType);
        if (subs == null) return;
        for (Sub s : subs.global) {
            if (s.consumer == listener) {
                remove(s);
                return;
            }
        }
    }

    /** Sorted on insert: after every sub of equal or higher priority, before the first lower one. */
    private static int insertionIndex(List<Sub> list, Sub sub) {
        int i = 0;
        for (Sub existing : list) {
            if (existing.priority.ordinal() > sub.priority.ordinal()) break;
            i++;
        }
        return i;
    }

    private void remove(Sub sub) {
        synchronized (lock) {
            if (!sub.active) return;
            sub.active = false;
            Subs subs = byType.get(sub.eventType);
            if (subs == null) return;
            if (sub.contextKey == null) {
                subs.global.remove(sub);
            } else {
                CopyOnWriteArrayList<Sub> list = subs.scoped.get(sub.contextKey);
                if (list != null) {
                    list.remove(sub);
                    if (list.isEmpty()) subs.scoped.remove(sub.contextKey, list);
                }
            }
        }
    }

    /* =============================================================
     * Dispatch
     * ========================================================== */

    /**
     * Whether posting an event of {@code eventType} for {@code contextKey} would reach anyone: a
     * global subscriber, or one filed under that key. The cheap check before building an event.
     *
     * @param contextKey the event's context key, or null for an unscoped event
     */
    public boolean hasSubscribers(Class<?> eventType, Object contextKey) {
        Subs subs = byType.get(eventType);
        if (subs == null) return false;
        if (!subs.global.isEmpty()) return true;
        if (contextKey == null) return false;
        List<Sub> list = subs.scoped.get(contextKey);
        return list != null && !list.isEmpty();
    }

    /**
     * Post an event to the consumers of its exact class, synchronously, in priority order. A {@link
     * ModuleSignal} also reaches those filed under its context's key. A consumer that throws
     * propagates out of this call and ends the dispatch.
     *
     * @throws IllegalStateException if dispatches nest deeper than {@value #MAX_DEPTH}
     */
    public <E> void post(E event) {
        Objects.requireNonNull(event, "event");

        Subs subs = byType.get(event.getClass());
        if (subs == null) return;

        ModuleSignal<?, ?> signal = event instanceof ModuleSignal<?, ?> ms ? ms : null;

        List<Sub> scoped = null;
        if (signal != null) {
            Object key = signal.contextKey();
            if (key != null) scoped = subs.scoped.get(key);
        }
        List<Sub> global = subs.global;
        boolean noScoped = scoped == null || scoped.isEmpty();
        if (global.isEmpty() && noScoped) return;

        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException(
                    "Event dispatch nested deeper than " + MAX_DEPTH + " while posting "
                            + event.getClass().getSimpleName()
                            + " -- a consumer is triggering its own event recursively.");
        }
        depth++;
        try {
            if (noScoped) {
                for (Sub s : global) deliver(s, event, signal);
            } else {
                Sub[] g = global.toArray(new Sub[0]);
                Sub[] c = scoped.toArray(new Sub[0]);
                int gi = 0, ci = 0;
                while (gi < g.length || ci < c.length) {
                    Sub next;
                    if (ci >= c.length || (gi < g.length && before(g[gi], c[ci]))) {
                        next = g[gi++];
                    } else {
                        next = c[ci++];
                    }
                    deliver(next, event, signal);
                }
            }
        } finally {
            depth--;
        }
    }

    private static boolean before(Sub a, Sub b) {
        int p = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
        return p != 0 ? p < 0 : a.sequence < b.sequence;
    }

    @SuppressWarnings("unchecked")
    private void deliver(Sub sub, Object event, ModuleSignal<?, ?> signal) {
        if (!sub.active) return;
        if (signal != null && !sub.receiveCancelled && signal.isCanceled()) return;

        ((Consumer<Object>) sub.consumer).accept(event);
    }

    /* =============================================================
     * Introspection (optional, debug-friendly)
     * ========================================================== */

    /**
     * @return all event types with at least one listener
     */
    public List<Class<?>> subscribedEventTypes() {
        return new ArrayList<>(byType.keySet());
    }

    /**
     * @return number of listeners registered for a given event type, global and scoped
     */
    public int listenerCount(Class<?> eventType) {
        Subs subs = byType.get(eventType);
        if (subs == null) return 0;
        int n = subs.global.size();
        for (List<Sub> list : subs.scoped.values()) n += list.size();
        return n;
    }
}
