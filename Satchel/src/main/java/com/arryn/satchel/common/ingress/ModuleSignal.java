package com.arryn.satchel.common.ingress;

import com.arryn.satchel.common.lifecycle.ContextKeyed;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Base for a module's own event: the clean-typed reflection of a native (Forge) event, built by the
 * module and posted to the {@code SatchelEventBus} by Satchel's ingress.
 *
 * <p>
 * The base holds the native event, protected, and the resolved context. It names no Forge type
 * ({@code N} is unconstrained); the registration layer, which does bind {@code N} to Forge's
 * {@code Event}, hands the base the one thing it needs from the native event, its cancel state.
 *
 * <ul>
 *   <li><b>Lazy.</b> A getter reads the native event when asked. The constructor copies only values
 *   that cannot change during dispatch (a UUID, a type id, a position as of the event). A value the
 *   event's own setters can change is read live, since another listener may have changed it.</li>
 *   <li><b>Valid only during dispatch,</b> like Forge's own events. After the last consumer returns,
 *   Satchel closes the event and a read of the native event throws, so a consumer that kept the event
 *   fails loudly in development. Snapshot fields stay readable, and so does {@link #isCanceled()}.</li>
 *   <li><b>No Forge types out.</b> A subclass's public getters return plain values, Satchel types,
 *   the module's own types and approved vanilla types. Vetoes are methods the module names,
 *   delegating to the native event.</li>
 *   <li><b>One resolved context.</b> {@code C} is usually a {@code ScopeInfo}, or a plain value when
 *   the resolver is only a cheap filter, or {@link Void} for an unscoped event.</li>
 * </ul>
 *
 * @param <N> the native event type, unconstrained here
 * @param <C> the resolved context
 */
public abstract class ModuleSignal<N, C> {

    private final N nativeEvent;
    private final C context;

    private BooleanSupplier canceled;
    private boolean closed;
    private boolean closedCanceled;

    /**
     * @param nativeEvent the event being reflected; never null
     * @param context     the resolved context; null for an unscoped event
     */
    protected ModuleSignal(N nativeEvent, C context) {
        this.nativeEvent = Objects.requireNonNull(nativeEvent, "nativeEvent");
        this.context = context;
    }

    /**
     * The native event, for the platform-bound subclass that translates it.
     *
     * @throws IllegalStateException after dispatch has ended
     */
    protected final N nativeEvent() {
        if (closed) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + "'s native event was read after dispatch ended. "
                            + "Copy what you need during dispatch; a module event is not valid afterward.");
        }
        return nativeEvent;
    }

    /**
     * The context the registration resolved, or null for an unscoped event.
     */
    public final C context() {
        return context;
    }

    /**
     * Whether the native event has been canceled, by this or an earlier listener. After dispatch it
     * answers the state the event ended with.
     */
    public final boolean isCanceled() {
        if (closed) return closedCanceled;
        return canceled != null && canceled.getAsBoolean();
    }

    /**
     * The key scoped subscriptions for this event are filed under: the context's own key if it is
     * {@link ContextKeyed}, the context itself otherwise, null for an unscoped event.
     */
    public final Object contextKey() {
        if (context instanceof ContextKeyed keyed) return keyed.contextKey();
        return context;
    }

    public final boolean isClosed() {
        return closed;
    }

    /**
     * Binds how to read the native event's cancel state. Called by Satchel's registration layer right
     * after the module's constructor returns; module code never calls it.
     */
    public final void bindCancelState(BooleanSupplier canceled) {
        this.canceled = Objects.requireNonNull(canceled, "canceled");
    }

    /**
     * Ends dispatch: reads of the native event throw from here on. Called by Satchel after the last
     * consumer returns; module code never calls it.
     */
    public final void close() {
        if (closed) return;
        closedCanceled = canceled != null && canceled.getAsBoolean();
        closed = true;
    }
}
