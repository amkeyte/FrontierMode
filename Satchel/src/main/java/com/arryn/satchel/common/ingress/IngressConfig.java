package com.arryn.satchel.common.ingress;

import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.lifecycle.SatchelEventBus;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A module's declaration of how it reacts to Forge events: which native events it subscribes to,
 * how each resolves its context, and the module event each produces. Registered as a side-keyed
 * factory ({@code Satchel.registerIngress(side, () -> Holder.build())}); each side's booter calls the
 * factories for its side at its own foundation boot and installs the result with that foundation, so
 * the ingress belongs to one foundation and its guard compares against exactly that one.
 *
 * <p>
 * Satchel registers the Forge listener for every subscription itself. {@code E extends Event} is
 * bound here, in the registration layer, and nowhere in the bases.
 *
 * <ul>
 *   <li>{@link #translate(Class, Function, BiFunction)} turns a Forge event into a module event with
 *   a resolved context; {@link #translate(Class, Function)} does so with none.</li>
 *   <li>{@link #handler(SatchelEventHandler)} registers a handler that does more than translate.</li>
 *   <li>{@link #priority} and {@link #receiveCancelled} set the Forge listener priority and
 *   cancelled-event delivery of the <i>next</i> subscription; they do not carry over past it.</li>
 *   <li>{@link #listenForAll} is a global consumer of a module event, owned by this config.</li>
 * </ul>
 *
 * <p>
 * Two priorities: the Forge listener priority orders when Satchel builds and posts the module event
 * among Forge listeners; the bus priority orders consumers of that event.
 *
 * <p>
 * Frozen when it is installed: a call after that throws. A factory builds a fresh config every time
 * it is called, so nothing is shared between the two sides of a {@code BOTH} registration.
 */
@PlatformBound(reason = "The registration layer: binds module events to Forge's event bus")
public final class IngressConfig {

    /** One thing to do to a foundation when this config installs. */
    private interface Installer {
        void install(LogicalFoundation foundation, String owner);
    }

    private final IngressKey key;
    private final List<Installer> subscriptions = new ArrayList<>();

    private EventPriority pendingPriority = EventPriority.NORMAL;
    private boolean pendingReceiveCancelled;
    private boolean frozen;

    public IngressConfig(IngressKey key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    public IngressKey key() {
        return key;
    }

    /* =============================================================
     * Forge listener options -- for the next subscription only
     * ========================================================== */

    /**
     * The Forge listener priority of the next {@code translate} or {@code handler}.
     */
    public IngressConfig priority(EventPriority priority) {
        requireOpen();
        this.pendingPriority = Objects.requireNonNull(priority, "priority");
        return this;
    }

    /**
     * Whether the next {@code translate} or {@code handler} also hears events an earlier Forge
     * listener cancelled.
     */
    public IngressConfig receiveCancelled() {
        requireOpen();
        this.pendingReceiveCancelled = true;
        return this;
    }

    /* =============================================================
     * Subscriptions to Forge events
     * ========================================================== */

    /**
     * Turns {@code nativeType} into a module event whose context the {@code resolver} finds.
     *
     * <p>
     * The resolver answers {@code native -> Optional<C>}; an empty answer ends the firing before
     * anything is built. It should be a cheap filter, since it runs for every firing (a type test,
     * then a lookup). The event's constructor runs only when the resolver answers and something on
     * the bus will receive the event.
     *
     * @param ctor usually the module event's constructor, {@code MyEvent::new}
     */
    public <N extends Event, C, S extends ModuleSignal<N, C>> IngressConfig translate(
            Class<N> nativeType,
            Function<N, Optional<C>> resolver,
            BiFunction<N, C, S> ctor
    ) {
        requireOpen();
        Objects.requireNonNull(nativeType, "nativeType");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(ctor, "ctor");

        subscribe(nativeType, owner -> new Translation<>(nativeType, resolver, ctor));
        return this;
    }

    /**
     * Turns {@code nativeType} into an unscoped module event, with no context to resolve. The
     * constructor runs for every firing while the event has a subscriber, so it must be cheap.
     */
    public <N extends Event, S extends ModuleSignal<N, Void>> IngressConfig translate(
            Class<N> nativeType,
            Function<N, S> ctor
    ) {
        requireOpen();
        Objects.requireNonNull(nativeType, "nativeType");
        Objects.requireNonNull(ctor, "ctor");

        subscribe(nativeType, owner -> new Translation<>(nativeType, ctor));
        return this;
    }

    /**
     * Registers a handler that does more than translate. It is stateless, and serves this config
     * only: build a fresh one in the factory.
     */
    public <N extends Event> IngressConfig handler(SatchelEventHandler<N> handler) {
        requireOpen();
        Objects.requireNonNull(handler, "handler");

        subscribe(handler.nativeType(), owner -> handler);
        return this;
    }

    private <N extends Event> void subscribe(
            Class<N> nativeType,
            Function<String, SatchelEventHandler<N>> handlerFor
    ) {
        EventPriority priority = pendingPriority;
        boolean receiveCancelled = pendingReceiveCancelled;
        pendingPriority = EventPriority.NORMAL;
        pendingReceiveCancelled = false;

        subscriptions.add((foundation, owner) -> {
            SatchelEventHandler<N> handler = handlerFor.apply(owner);
            handler.bind(foundation, owner);
            MinecraftForge.EVENT_BUS.addListener(priority, receiveCancelled, nativeType, handler::onEvent);
        });
    }

    /* =============================================================
     * Global consumers of module events
     * ========================================================== */

    /**
     * Hears a module event for every firing, owned by this config: installed on each side's bus at
     * foundation boot and kept for the JVM. No jig is needed. Set its bus priority and
     * cancelled-event delivery on the returned handle.
     */
    public <S> Listening listenForAll(Class<S> type, Consumer<? super S> handler) {
        requireOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");

        Listening listening = new Listening();
        subscriptions.add((foundation, owner) -> foundation.eventBus().subscribe(
                type, null, listening.priority, listening.receiveCancelled, owner, handler));
        return listening;
    }

    /**
     * The options of one {@link #listenForAll} consumer: its place among the consumers of the event
     * (bus priority), and whether it hears an event a consumer before it cancelled.
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

    /* =============================================================
     * Installation
     * ========================================================== */

    /**
     * Freezes the config and installs it with {@code foundation}: registers its Forge listeners and
     * subscribes its consumers on the foundation's bus. Called once by the booter.
     */
    void install(LogicalFoundation foundation) {
        Objects.requireNonNull(foundation, "foundation");
        if (frozen) {
            throw new IllegalStateException("Ingress " + key.name + " is already installed");
        }
        frozen = true;

        for (Installer installer : subscriptions) {
            installer.install(foundation, key.name);
        }
    }

    private void requireOpen() {
        if (frozen) {
            throw new IllegalStateException(
                    "Ingress " + key.name + " is installed and frozen; declare its subscriptions in the factory");
        }
    }
}
