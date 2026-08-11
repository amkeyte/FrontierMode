package com.arryn.satchel.common.jig.guts;

import net.minecraftforge.fml.LogicalSide;

import java.util.Objects;
import java.util.Optional;

/**
 * Thread-bound logical identity context.
 *
 * <p>
 * This class answers exactly one question:
 * <blockquote>
 *   "Which logical side is this thread currently acting as?"
 * </blockquote>
 *
 * It does NOT:
 *  - track execution scopes
 *  - manage scopeLifecycle state
 *  - own Satchel infrastructure
 *  - infer or guess identity
 */
public final class LogicalSideContext {

    private static final ThreadLocal<LogicalSide> SIDE = new ThreadLocal<>();

    private LogicalSideContext() {}

    /* =============================================================
     * Binding
     * ========================================================== */

    /**
     * Bind a logical side to the current thread.
     *
     * This should be called at the first ingress point for a thread
     * (Forge event, command execution, packet handling, etc).
     */
    public static void bind(LogicalSide side) {
        SIDE.set(Objects.requireNonNull(side, "side"));
    }

    /**
     * Clear the logical side binding for the current thread.
     *
     * This should only be used when explicitly handing off or
     * tearing down worker threads.
     */
    public static void clear() {
        SIDE.remove();
    }

    /* =============================================================
     * Access
     * ========================================================== */

    /**
     * @return the currently bound logical side, if any
     */
    public static Optional<LogicalSide> current() {
        return Optional.ofNullable(SIDE.get());
    }

    /**
     * @return the currently bound logical side
     * @throws IllegalStateException if no side is bound
     */
    public static LogicalSide require() {
        LogicalSide side = SIDE.get();
        if (side == null) {
            throw new IllegalStateException(
                    "No LogicalSide bound to thread '" +
                            Thread.currentThread().getName() + "'.\n" +
                            "This thread is not executing inside a Satchel-aware ingress."
            );
        }
        return side;
    }

    /* =============================================================
     * Utilities
     * ========================================================== */

    /**
     * Execute a task with an explicitly bound logical side.
     *
     * Useful for worker threads or controlled handoff.
     */
    public static void runWith(
            LogicalSide side,
            Runnable task
    ) {
        Objects.requireNonNull(task, "task");

        LogicalSide previous = SIDE.get();
        SIDE.set(side);
        try {
            task.run();
        } finally {
            if (previous == null) {
                SIDE.remove();
            } else {
                SIDE.set(previous);
            }
        }
    }
}
