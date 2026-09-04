package com.arryn.satchel.common.lifecycle;

import com.arryn.satchel.common.identity.JigKey;
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
        ScopeEvent.Unloaded,
        ScopeEvent.MobGainedInterest,
        ScopeEvent.MobLostInterest {

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
        return key == info().jigInfo().key;
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

    /* =============================================================
     * Mob-kind additive signals (SAT_044)
     * ========================================================== */

    /**
     * Emitted by {@link com.arryn.satchel.common.jig.mob.MobJig} immediately after
     * {@link Loaded} fires for a mob scope — additively, never instead of the generic
     * event. Honest rename: "loaded" borrows LevelScope vocabulary; "gained interest"
     * is what the interest-registry poll actually detected.
     *
     * <p>
     * {@link ScopeInfo} guarantees are identical to those of {@link Loaded}: the scope
     * is stable and authoritative for the duration of the handler.
     */
    public static final class MobGainedInterest extends ScopeEvent {
        public MobGainedInterest(ScopeInfo info) {
            super(info);
        }
    }

    /**
     * Emitted by {@link com.arryn.satchel.common.jig.mob.MobJig} immediately after
     * {@link Unloaded} fires for a mob scope — additively, never instead of the generic
     * event. The scope has already been evicted from {@code JigInfo} by the time this
     * fires; the {@link ScopeInfo} object is still valid for reading but is no longer
     * resident in the jig.
     */
    public static final class MobLostInterest extends ScopeEvent {
        public MobLostInterest(ScopeInfo info) {
            super(info);
        }
    }
}
