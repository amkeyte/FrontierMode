package com.arryn.satchel.common.lifecycle;

import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

import java.util.Objects;

/**
 * Emits Satchel scopeInfo lifecycle events.
 *
 * <p>
 * This dispatcher does NOT:
 * <ul>
 *   <li>Discover scopes</li>
 *   <li>Decide readiness</li>
 *   <li>Invoke requireJig execution</li>
 * </ul>
 *
 * <p>
 * It only:
 * <ul>
 *   <li>Transitions {@link ScopeInfo} phase</li>
 *   <li>Emits corresponding {@link ScopeEvent}s</li>
 * </ul>
 */
public final class ScopeLifecycleDispatcher {

    private final LogicalFoundation foundation;

    public ScopeLifecycleDispatcher(LogicalFoundation foundation) {
        this.foundation = Objects.requireNonNull(foundation, "foundation");
    }

    /* =============================================================
     * Lifecycle transitions (per requireJig + scopeInfo)
     * ========================================================== */

    /**
     * Promote a scopeInfo to LOADED for a specific requireJig.
     *
     * <p>
     * Must only be called after readiness has been accepted
     * by the requireJig's coupler.
     */
    public void signalScopeLoaded(ScopeInfo info) {
        Objects.requireNonNull(info, "info");

        if (info.phase() != ScopeInfo.Phase.NEW) {
            throw new IllegalStateException(
                    "Scope already loaded or unloading: " +
                            info.scope().debugName()
            );
        }

        info.setPhase(ScopeInfo.Phase.LOADED);
        emitLoaded(info);
    }

    /**
     * Emit a semantic tick for a loaded scopeInfo.
     *
     * <p>
     * Caller is responsible for ensuring execution work
     * has already been performed.
     */
    public void signalScopeTick(ScopeInfo info) {
        Objects.requireNonNull(info, "info");

        if (info.phase() != ScopeInfo.Phase.LOADED) {
            return; // not tickable
        }

        emitTick(info);
    }

    /**
     * Begin unloading a scopeInfo for a specific requireJig.
     */
    public void signalScopeUnloaded(ScopeInfo info) {
        Objects.requireNonNull(info, "info");

        if (info.phase() != ScopeInfo.Phase.LOADED) {
            return;
        }

        info.setPhase(ScopeInfo.Phase.UNLOADING);
        emitUnloaded(info);
        info.setPhase(ScopeInfo.Phase.UNLOADED);
        var ji =  info.jigInfo();
        if (ji==null) throw new IllegalStateException("how!!?!?");
        ji.removeScope(info.scope());
    }

    /* =============================================================
     * Event emission
     * ========================================================== */

    private void emitLoaded(ScopeInfo info) {
        foundation.eventBus()
                .post(new ScopeEvent.Loaded(info));
    }

    private void emitTick(ScopeInfo info) {
        foundation.eventBus()
                .post(new ScopeEvent.Tick(info));
    }

    private void emitUnloaded(ScopeInfo info) {
        foundation.eventBus()
                .post(new ScopeEvent.Unloaded(info));
    }
}
