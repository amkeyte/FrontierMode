package com.arryn.satchel.common.ingress;

/**
 * The root of every module event: what any consumer may ask of one, whatever line built it.
 *
 * <p>
 * A module event that runs on two lines is an interface the module writes in {@code shared}, extending
 * this one with its getters, live reads and named vetoes. Each line implements it with a class that
 * extends {@link ModuleSignal} on that line's native event, and registers it with the {@code translate}
 * form that names the interface, so the event is posted under the interface and a consumer that names
 * the interface hears it from every line. See Module Events, "A module event on two lines".
 *
 * <p>
 * A module event class that consumers name directly, for a module on one line, implements this too,
 * through {@link ModuleSignal}.
 *
 * @param <C> the resolved context: usually a {@code ScopeInfo}, a plain value when the resolver is
 *            only a cheap filter, or {@link Void} for an unscoped event
 */
public interface ModuleEvent<C> {

    /**
     * The context the registration resolved, or null for an unscoped event.
     */
    C context();

    /**
     * Whether the native event has been canceled, by this or an earlier listener. After dispatch it
     * answers the state the event ended with.
     */
    boolean isCanceled();
}
