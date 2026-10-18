package com.arryn.satchel.common.ingress;

import com.arryn.satchel.common.lifecycle.ContextKeyed;
import net.minecraftforge.eventbus.api.Event;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The handler {@link IngressConfig#translate} builds: turns one Forge event into one module event.
 * After the foundation comparison (see {@link SatchelEventHandler}) it applies:
 * <ol>
 *   <li><b>The resolver bounce.</b> The module's resolver answers the context ({@code native ->
 *   Optional<C>}); an empty answer ends the firing before anything is built or posted. A
 *   registration with no context has no resolver.</li>
 *   <li><b>Cheap rejection before allocation.</b> The module event is built only if something on the
 *   bus will receive it: a global subscriber, or one filed under this firing's context. The event's
 *   class is learned from the first one built, so the first firing builds once regardless.</li>
 * </ol>
 * Then the event is bound to the native event's cancel state and posted, and closed after the last
 * consumer returns.
 */
final class Translation<N extends Event, C, S extends ModuleSignal<N, C>> extends SatchelEventHandler<N> {

    private final Function<N, Optional<C>> resolver;
    private final BiFunction<N, C, S> scopedCtor;
    private final Function<N, S> unscopedCtor;

    /** Learned from the first event built; null until then. */
    private Class<?> eventType;

    /** A scoped translation. */
    Translation(Class<N> nativeType, Function<N, Optional<C>> resolver, BiFunction<N, C, S> ctor) {
        super(nativeType);
        this.resolver = resolver;
        this.scopedCtor = ctor;
        this.unscopedCtor = null;
    }

    /** An unscoped translation, with no context to resolve. */
    Translation(Class<N> nativeType, Function<N, S> ctor) {
        super(nativeType);
        this.resolver = null;
        this.scopedCtor = null;
        this.unscopedCtor = ctor;
    }

    @Override
    protected void handle(N event) {
        C context = null;
        if (resolver != null) {
            Optional<C> resolved = resolver.apply(event);
            if (resolved.isEmpty()) return;
            context = resolved.get();
        }

        Class<?> type = eventType;
        if (type != null) {
            Object key = context instanceof ContextKeyed keyed ? keyed.contextKey() : context;
            if (!bus().hasSubscribers(type, key)) return;
        }

        S signal = resolver != null ? scopedCtor.apply(event, context) : unscopedCtor.apply(event);
        signal.bindCancelState(event::isCanceled);
        eventType = signal.getClass();

        post(signal);
    }
}
