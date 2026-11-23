package com.arryn.satchel.common.ingress;

import com.arryn.satchel.common.lifecycle.SatchelEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A module's global consumers of module events, declared in {@code shared}: each hears an event for every
 * firing. An ingress config installs them on its foundation's bus at boot ({@code ingress.consumers(...)}),
 * owned by that config, for the life of the JVM; no jig is needed. A module on two lines declares its
 * consumers here, naming its event interfaces, so they read without the line folders; each line's ingress
 * config only translates and hands them over. {@code IngressConfig.listenForAll} declares one the same way.
 *
 * <p>
 * Build a fresh one in the side-keyed factory, as the ingress config is. A consumer is stateless: it lives as
 * long as the JVM while an integrated server starts and stops inside it. Declaring one after these consumers
 * have been installed throws, since it would never be installed.
 */
public final class GlobalConsumers {

    private final List<Entry<?>> entries = new ArrayList<>();
    private boolean installed;

    /**
     * Hears {@code type} for every firing. Set its bus priority and cancelled-event delivery on the returned
     * handle.
     *
     * @param type the event's type as it is posted: a module's event interface, or a module event class
     */
    public <S> Listening listenForAll(Class<S> type, Consumer<? super S> handler) {
        if (installed) {
            throw new IllegalStateException("A consumer of " + type.getSimpleName()
                    + " was declared after these consumers were installed, so it would never be installed");
        }
        Entry<S> entry = new Entry<>(Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(handler, "handler"));
        entries.add(entry);
        return entry.listening;
    }

    /**
     * Subscribes every consumer on {@code bus}, owned by {@code owner}. Called by the ingress config that
     * installs these; module code never calls it.
     */
    public void install(SatchelEventBus bus, String owner) {
        Objects.requireNonNull(bus, "bus");
        installed = true;
        for (Entry<?> entry : entries) entry.subscribe(bus, owner);
    }

    /**
     * The options of one consumer: its place among the consumers of the event (bus priority), and whether it
     * hears an event a consumer before it cancelled.
     */
    public static final class Listening {

        private SatchelEventBus.Priority priority = SatchelEventBus.Priority.NORMAL;
        private boolean receiveCancelled;

        private Listening() {
        }

        public Listening priority(SatchelEventBus.Priority priority) {
            this.priority = Objects.requireNonNull(priority, "priority");
            return this;
        }

        public Listening receiveCancelled() {
            this.receiveCancelled = true;
            return this;
        }
    }

    private static final class Entry<S> {
        final Class<S> type;
        final Consumer<? super S> handler;
        final Listening listening = new Listening();

        Entry(Class<S> type, Consumer<? super S> handler) {
            this.type = type;
            this.handler = handler;
        }

        void subscribe(SatchelEventBus bus, String owner) {
            bus.subscribe(type, null, listening.priority, listening.receiveCancelled, owner, handler);
        }
    }
}
