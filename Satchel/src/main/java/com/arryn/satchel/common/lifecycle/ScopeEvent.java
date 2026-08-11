package com.arryn.satchel.common.lifecycle;

import com.arryn.satchel.common.jig.guts.ScopeInfo;

import java.util.Objects;

/**
 * Base scopeType for Satchel scopeInfo lifecycle events.
 *
 * <p>
 * All {@link ScopeEvent}s are emitted by the {@link ScopeLifecycleDispatcher}
 * after authoritative lifecycle state has been updated in {@link ScopeInfo}.
 *
 * <p>
 * If a ScopeEvent is delivered:
 * <ul>
 *   <li>The {@link ScopeInfo} instance is authoritative and stable</li>
 *   <li>The underlying scopeInfo is valid for the duration of the handler</li>
 *   <li>No additional guards or availability checks are required</li>
 * </ul>
 */
public abstract sealed class ScopeEvent
        permits ScopeEvent.Loaded,
        ScopeEvent.Tick,
        ScopeEvent.Unloaded {

    private final ScopeInfo info;

    protected ScopeEvent(ScopeInfo info) {
        this.info = Objects.requireNonNull(info, "info");
    }


    /**
     * Authoritative lifecycle record for the scopeInfo.
     */
    public final ScopeInfo info() {
        return info;
    }

    /* =============================================================
     * Scope lifecycle events
     * ========================================================== */

    /**
     * Emitted exactly once when a scopeInfo is recognized by Satchel
     * and enters the {@link ScopeInfo.Phase#LOADED} phase.
     *
     * <p>
     * This event does NOT imply persistence, readiness, or execution
     * activation — only that the scopeInfo is now known and trackable.
     */
    public static final class Loaded extends ScopeEvent {
        public Loaded(ScopeInfo info) {
            super(info);
        }
    }

    /**
     * Emitted for each valid semantic tick of a loaded scopeInfo.
     *
     * <p>
     * Execution and maintenance work for the scopeInfo will have already
     * been performed by Satchel prior to this event being delivered.
     */
    public static final class Tick extends ScopeEvent {
        public Tick(ScopeInfo info) {
            super(info);
        }
    }

    /**
     * Emitted exactly once when a scopeInfo enters teardown.
     *
     * <p>
     * This is the final guaranteed safe access point for the scopeInfo
     * and any data associated with it.
     */
    public static final class Unloaded extends ScopeEvent {
        public Unloaded(ScopeInfo info) {
            super(info);
        }
    }
}
