package com.arryn.satchel.common.lifecycle;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;

import java.util.Objects;

/**
 * Base scopeType for Satchel scopeInfo lifecycle events.
 *
 * <p>
 * All {@link ScopeEvent}s are emitted by the {@link ScopeLifecycleDispatcher}
 * after authoritative lifecycle state has been updated in {@link ScopeInfoImpl}.
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


    /**
     * Returns {@code true} if this event was emitted by the jig config identified by
     * {@code key}. Use as a consumer-side guard when multiple jig configs of the same
     * type may track overlapping scopes:
     *
     * <pre>{@code
     * if (!event.isJig(MY_JIG)) return;
     * }</pre>
     *
     * Identity comparison -- {@link JigKey} instances are registered singletons.
     */
    public final boolean isJig(JigKey<?> key) {
        return key == info().jigKey();
    }

    /* =============================================================
     * Scope lifecycle events
     * ========================================================== */

    /**
     * Emitted exactly once when a scopeInfo is recognized by Satchel
     * and enters the {@link ScopeInfoImpl.Phase#LOADED} phase.
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
