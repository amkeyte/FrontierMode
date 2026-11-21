package com.arryn.satchel.common.bundle;

import java.util.EnumSet;
import java.util.Objects;

public final class LifecycleGuard {

    private LifecycleState state;

    public LifecycleGuard(LifecycleState initial) {
        this.state = Objects.requireNonNull(initial);
    }

    public LifecycleState state() {
        return state;
    }

    /** Enforce exact state */
    public void require(LifecycleState expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Operation not allowed in state " + state +
                            " (expected " + expected + ")"
            );
        }
    }

    /** Enforce one-of states */
    public void requireAny(EnumSet<LifecycleState> allowed) {
        if (!allowed.contains(state)) {
            throw new IllegalStateException(
                    "Operation not allowed in state " + state +
                            " (allowed " + allowed + ")"
            );
        }
    }

    /** Strict transition */
    public void transition(LifecycleState expected, LifecycleState next) {
        require(expected);
        state = next;
    }

    /** Loose transition (used when caller already validated) */
    public void force(LifecycleState next) {
        state = Objects.requireNonNull(next);
    }

    /** Terminal guard */
    public boolean isTerminal() {
        return state == LifecycleState.DESTROYED;
    }
}
