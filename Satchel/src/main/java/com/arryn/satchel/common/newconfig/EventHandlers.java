package com.arryn.satchel.common.newconfig;

import com.arryn.satchel.common.lifecycle.SatchelEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Declarative collection of event subscriptions.
 *
 * <p>
 * EventHandlers do NOT:
 * <ul>
 *   <li>emit events</li>
 *   <li>control lifecycle</li>
 *   <li>infer readiness</li>
 *   <li>perform execution</li>
 * </ul>
 *
 * They only install subscriptions onto a {@link SatchelEventBus}.
 */
public final class EventHandlers {

    private final List<Consumer<SatchelEventBus>> installers;

    private EventHandlers(List<Consumer<SatchelEventBus>> installers) {
        this.installers = installers;
    }

    /**
     * Install all handlers into the given event bus.
     *
     * This method is idempotent only if the caller ensures it is called once.
     */
    public void install(SatchelEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        for (Consumer<SatchelEventBus> installer : installers) {
            installer.accept(bus);
        }
    }

    // ---------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<Consumer<SatchelEventBus>> installers =
                new ArrayList<>();

        /**
         * Subscribe a handler to a specific event type.
         */
        public <E> Builder on(
                Class<E> eventType,
                Consumer<? super E> handler
        ) {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(handler, "handler");

            installers.add(bus -> bus.subscribe(eventType, handler));
            return this;
        }

        /**
         * Install a custom installer (escape hatch).
         *
         * Useful for advanced wiring or bulk subscription logic.
         */
        public Builder installWith(
                Consumer<SatchelEventBus> installer
        ) {
            Objects.requireNonNull(installer, "installer");
            installers.add(installer);
            return this;
        }

        public EventHandlers build() {
            return new EventHandlers(List.copyOf(installers));
        }
    }
}
