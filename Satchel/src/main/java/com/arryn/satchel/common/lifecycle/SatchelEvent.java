package com.arryn.satchel.common.lifecycle;

import net.minecraftforge.fml.LogicalSide;

import java.util.Objects;

/**
 * Base scopeType for Satchel foundation scopeLifecycle events.
 *
 * These describe the operational state of Satchel on a logical side,
 * independent of any scopes.
 */
public abstract sealed class SatchelEvent
        permits SatchelEvent.Started,
        SatchelEvent.Stopped {

    private final LogicalSide side;

    protected SatchelEvent(LogicalSide side) {
        this.side = Objects.requireNonNull(side, "side");
    }

    public final LogicalSide side() {
        return side;
    }

    /* =============================================================
     * Events
     * ========================================================== */

    /**
     * Emitted exactly once when Satchel becomes operational
     * on this logical side.
     */
    public static final class Started extends SatchelEvent {
        public Started(LogicalSide side) {
            super(side);
        }
    }

    /**
     * Emitted exactly once when Satchel shuts down
     * on this logical side.
     */
    public static final class Stopped extends SatchelEvent {
        public Stopped(LogicalSide side) {
            super(side);
        }
    }
}
