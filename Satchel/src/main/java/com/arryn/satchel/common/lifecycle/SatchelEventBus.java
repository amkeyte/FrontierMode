package com.arryn.satchel.common.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple synchronous event bus for Satchel scopeLifecycle events.
 *
 * <p>
 * This bus:
 *  - is owned by a LogicalFoundation
 *  - delivers events synchronously
 *  - guarantees ordering
 *  - does not perform filtering, inference, or scopeLifecycle checks
 *
 * If an event is posted here, all invariants have already been enforced.
 */
public final class SatchelEventBus {

    /**
     * Listener lists keyed by event scopeType.
     */
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners =
            new ConcurrentHashMap<>();

    /* =============================================================
     * Subscription
     * ========================================================== */

    /**
     * Subscribe to a specific Satchel event scopeType.
     *
     * @param eventType the concrete event class
     * @param listener  the event consumer
     */
    public <E> void subscribe(
            Class<E> eventType,
            Consumer<? super E> listener
    ) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");

        listeners
                .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    /**
     * Unsubscribe a listener from a specific event scopeType.
     */
    public <E> void unsubscribe(
            Class<E> eventType,
            Consumer<? super E> listener
    ) {
        List<Consumer<?>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }

    /* =============================================================
     * Dispatch
     * ========================================================== */

    /**
     * Post an event to all registered listeners.
     *
     * Delivery is synchronous and ordered by subscription.
     */
    @SuppressWarnings("unchecked")
    public <E> void post(E event) {
        Objects.requireNonNull(event, "event");

        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }

        for (Consumer<?> raw : list) {
            ((Consumer<E>) raw).accept(event);
        }
    }

    /* =============================================================
     * Introspection (optional, debug-friendly)
     * ========================================================== */

    /**
     * @return all event types with at least one listener
     */
    public List<Class<?>> subscribedEventTypes() {
        return new ArrayList<>(listeners.keySet());
    }

    /**
     * @return number of listeners registered for a given event scopeType
     */
    public int listenerCount(Class<?> eventType) {
        List<Consumer<?>> list = listeners.get(eventType);
        return list == null ? 0 : list.size();
    }
}
