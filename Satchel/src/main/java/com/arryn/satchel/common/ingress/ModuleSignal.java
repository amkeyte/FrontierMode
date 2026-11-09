package com.arryn.satchel.common.ingress;

import com.arryn.satchel.common.lifecycle.ContextKeyed;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Base for a module's own event: the clean-typed reflection of a native (Forge) event, built by the
 * module and posted to the {@code SatchelEventBus} by Satchel's ingress.
 *
 * <p>
 * The base holds the native event, protected, and the resolved context. It names no Forge type
 * ({@code N} is unconstrained); the registration layer, which does bind {@code N} to Forge's
 * {@code Event}, hands the base what it needs from the native event: its cancel state, and its
 * result if it carries one.
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
 *   <li><b>A result, for a {@code @HasResult} native event.</b> {@link #allow()} and {@link #deny()}
 *   set it, a deny is sticky and an allow does not override one, and {@link #verdict()} reads it.
 *   An event nobody touches keeps the native event's default.</li>
 * </ul>
 *
 * @param <N> the native event type, unconstrained here
 * @param <C> the resolved context
 */
public abstract class ModuleSignal<N, C> {

    /**
     * The result of a native event that carries one, in Satchel's own terms: {@code DEFAULT} leaves
     * the decision to the game (or to a later listener), {@code ALLOW} forces it through, {@code DENY}
     * stops it.
     */
    public enum Verdict {
        DEFAULT, ALLOW, DENY
    }

    private final N nativeEvent;
    private final C context;

    private BooleanSupplier canceled;
    private Supplier<Verdict> verdictReader;
    private Consumer<Verdict> verdictWriter;
    private boolean closed;
    private boolean closedCanceled;
    private Verdict closedVerdict;

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
     * Allows the operation the native event decides, unless it is already denied: a deny is sticky,
     * so an allow after one (from this module, another, or any earlier listener) leaves it denied.
     *
     * @throws IllegalStateException if the native event carries no result, or dispatch has ended
     */
    public final void allow() {
        requireResult();
        if (verdictReader.get() != Verdict.DENY) verdictWriter.accept(Verdict.ALLOW);
    }

    /**
     * Denies the operation the native event decides. Sticky: nothing later in this dispatch turns it
     * back into an allow.
     *
     * @throws IllegalStateException if the native event carries no result, or dispatch has ended
     */
    public final void deny() {
        requireResult();
        verdictWriter.accept(Verdict.DENY);
    }

    /**
     * The native event's result right now. After dispatch it answers the result the event ended with.
     *
     * @throws IllegalStateException if the native event carries no result
     */
    public final Verdict verdict() {
        if (verdictReader == null) throw notAResultEvent();
        return closed ? closedVerdict : verdictReader.get();
    }

    private void requireResult() {
        if (verdictReader == null) throw notAResultEvent();
        if (closed) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + "'s result was changed after dispatch ended.");
        }
    }

    private IllegalStateException notAResultEvent() {
        return new IllegalStateException(
                getClass().getSimpleName() + " carries no result: its native event is not @HasResult.");
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
     * Binds how to read and write the native event's result. Called by Satchel's registration layer
     * for a native event that carries one; module code never calls it.
     */
    public final void bindResult(Supplier<Verdict> reader, Consumer<Verdict> writer) {
        this.verdictReader = Objects.requireNonNull(reader, "reader");
        this.verdictWriter = Objects.requireNonNull(writer, "writer");
    }

    /**
     * Ends dispatch: reads of the native event throw from here on. Called by Satchel after the last
     * consumer returns; module code never calls it.
     */
    public final void close() {
        if (closed) return;
        closedCanceled = canceled != null && canceled.getAsBoolean();
        closedVerdict = verdictReader == null ? null : verdictReader.get();
        closed = true;
    }
}
