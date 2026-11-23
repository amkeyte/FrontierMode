package com.arryn.satchel.common.ingress;

import com.arryn.satchel.common.lifecycle.ContextKeyed;
import com.arryn.satchel.common.platform.PlatformBound;
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
 *   bus will receive it: a global subscriber, or one filed under this firing's context. A registration
 *   that names the module's event interface posts under it and checks it before building anything; one
 *   that does not learns the event's class from the first one built, so its first firing builds once
 *   regardless.</li>
 * </ol>
 * Then the event is bound to the native event's cancel state, and to its result if the native event
 * carries one ({@code @HasResult}), and posted, and closed after the last consumer returns.
 */
@PlatformBound(reason = "ingress")
final class Translation<N extends Event, C, S extends ModuleSignal<N, C>> extends SatchelEventHandler<N> {

    private final Function<N, Optional<C>> resolver;
    private final BiFunction<N, C, S> scopedCtor;
    private final Function<N, S> unscopedCtor;

    /** The module's event interface the event is posted as, or null to post it as its own class. */
    private final Class<? extends ModuleEvent<?>> declaredType;

    /** The type posted under: the declared interface, or the class learned from the first event built. */
    private Class<?> eventType;

    /** A scoped translation. */
    Translation(Class<N> nativeType, Class<? extends ModuleEvent<?>> declaredType,
                Function<N, Optional<C>> resolver, BiFunction<N, C, S> ctor) {
        super(nativeType);
        this.declaredType = declaredType;
        this.eventType = declaredType;
        this.resolver = resolver;
        this.scopedCtor = ctor;
        this.unscopedCtor = null;
    }

    /** An unscoped translation, with no context to resolve. */
    Translation(Class<N> nativeType, Class<? extends ModuleEvent<?>> declaredType, Function<N, S> ctor) {
        super(nativeType);
        this.declaredType = declaredType;
        this.eventType = declaredType;
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
        if (declaredType != null && !declaredType.isInstance(signal)) {
            throw new IllegalStateException(signal.getClass().getName() + " was built for a registration posted as "
                    + declaredType.getName() + ", which it does not implement");
        }
        signal.bindCancelState(event::isCanceled);
        if (event.hasResult()) {
            signal.bindResult(() -> verdictOf(event.getResult()), v -> event.setResult(resultOf(v)));
        }

        if (declaredType != null) {
            post(signal, declaredType);
        } else {
            eventType = signal.getClass();
            post(signal);
        }
    }

    private static ModuleSignal.Verdict verdictOf(Event.Result result) {
        return switch (result) {
            case ALLOW -> ModuleSignal.Verdict.ALLOW;
            case DENY -> ModuleSignal.Verdict.DENY;
            case DEFAULT -> ModuleSignal.Verdict.DEFAULT;
        };
    }

    private static Event.Result resultOf(ModuleSignal.Verdict verdict) {
        return switch (verdict) {
            case ALLOW -> Event.Result.ALLOW;
            case DENY -> Event.Result.DENY;
            case DEFAULT -> Event.Result.DEFAULT;
        };
    }
}
