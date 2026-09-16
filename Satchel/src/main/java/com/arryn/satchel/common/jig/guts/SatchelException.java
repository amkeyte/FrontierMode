package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.LogicalSide;

/**
 * Base exception for jig-related access failures.
 *
 * <p>
 * Nine subclasses, one per failure shape: {@link AccessDenied} (an operation isn't
 * permitted -- policy/lifecycle/sidedness), {@link AccessFailed} (an operation was
 * permitted but failed on an underlying error -- IO, persistence, corruption), {@link
 * BundleNotFound} (a bundle hasn't been created yet for a scope -- the one subtype that's
 * routinely caught as expected control flow, see its own constructors), {@link
 * ScopeNotReady} (a known scope hasn't converged to {@code LOADED} yet), {@link
 * ScopeNotFound} (no {@code ScopeInfo} exists for the requested scope), {@link JigNotFound}
 * (no matching jig exists for the requested key), {@link Generic} (a catch-all for a
 * failure that doesn't fit any of the above), {@link NotReady} (code touched
 * Satchel-dependent state before {@code Satchel.isReady()} for the calling side -- see its
 * own doc for how this differs from {@link ScopeNotReady}), and {@link BadLogicalSide} (a
 * thread claimed the wrong {@link LogicalSide}).
 *
 * <p>
 * Every subtype logs unconditionally at construction (see the two-constructor pattern
 * below) except {@link BundleNotFound}, the one subtype that's genuinely routine, not an
 * error -- see the silent-variant constructor's own doc for why.
 */
public abstract class SatchelException extends RuntimeException {

    protected SatchelException(String message) {
        super(message);
        OUT.error(message);
    }

    protected SatchelException(String message, Throwable cause) {
        super(message, cause);
        OUT.error(message, cause);
    }

    /**
     * Silent variant -- for a subtype that is routinely thrown and caught as expected control
     * flow rather than surfaced as a real error (see {@link BundleNotFound}'s own constructors).
     * The two constructors above log at construction time, unconditionally, which is the right
     * call for every other subtype here: none of them are caught anywhere in the codebase
     * (confirmed by a repo-wide grep), so logging eagerly is the only way to guarantee a failure
     * is visible even if some future caller catches and silently swallows it. {@code
     * BundleNotFound} is the one exception -- {@code AScopeCoupler.getOrCreate} catches it every
     * time {@code get()}'s internal lookup misses, specifically to fall through to creating the
     * bundle. That's a normal "doesn't exist yet" event, not an error, but the old unconditional
     * super(message) log meant every single scope-load across every jig kind logged an ERROR
     * line for it anyway, doubled up with the WARN {@code getOrCreate} already logs for the same
     * event right after. This constructor lets {@code BundleNotFound} opt out of that eager log
     * without changing anything about the other subtypes, and without going silent on a genuine
     * failure either: a {@code BundleNotFound} thrown somewhere that does NOT catch it (e.g.
     * {@code get()} called directly rather than through {@code getOrCreate}) is still an
     * uncaught {@code RuntimeException} and still surfaces via the JVM's/Forge's own stack trace
     * reporting regardless of whether this constructor pre-logged it.
     */
    protected SatchelException(String message, boolean logAsError) {
        super(message);
        if (logAsError) {
            OUT.error(message);
        }
    }

    /**
     * Cause-carrying counterpart to the silent variant above -- same reasoning, just preserves
     * the cause chain instead of dropping it, for a subtype that wants both.
     */
    protected SatchelException(String message, Throwable cause, boolean logAsError) {
        super(message, cause);
        if (logAsError) {
            OUT.error(message, cause);
        }
    }

    /* =============================================================
     * Access denied (policy / lifecycle violation)
     * ========================================================== */

    /**
     * Thrown when an operation is not permitted due to
     * lifecycle state, logical side, or jig policy.
     * <p>
     * This indicates a programmer or integration error.
     */
    public static final class AccessDenied extends SatchelException {
        private static final String PREAMBLE =
                "A jig has been denied: ";

        public AccessDenied(String message) {
            super(PREAMBLE + message);
        }

        public AccessDenied(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
        }
    }

    /* =============================================================
     * Access failed (operational failure)
     * ========================================================== */

    /**
     * Thrown when an operation was permitted but failed
     * due to an underlying error (IO, persistence, corruption, etc).
     * <p>
     * This indicates an operational failure.
     */
    public static final class AccessFailed extends SatchelException {
        private static final String PREAMBLE =
                "A jig request has failed: ";

        public AccessFailed(String message) {
            super(PREAMBLE + message);
        }

        public AccessFailed(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
        }
    }

    public static final class BundleNotFound extends SatchelException {
        private static final String PREAMBLE =
                "A requested bundle was not found: ";

        public BundleNotFound(String message) {
            super(PREAMBLE + message, false);
        }

        public BundleNotFound(String message, Throwable cause) {
            // Unused today (every real throw site passes no cause), but kept consistent with
            // the no-arg constructor above -- same exception type, same expected-control-flow
            // role, so the same silent behavior applies if a cause-carrying variant ever is used.
            super(PREAMBLE + message, cause, false);
        }
    }

    public static final class ScopeNotReady extends SatchelException {
        private static final String PREAMBLE =
                "A Jig scopeInfo was not ready: ";

        public ScopeNotReady(String message) {
            super(PREAMBLE + message);
        }

        public ScopeNotReady(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
        }
    }

    public static final class ScopeNotFound extends SatchelException {
        private static final String PREAMBLE =
                "No Jig scopeInfo was found: ";

        public ScopeNotFound(String message) {
            super(PREAMBLE + message);
        }

        public ScopeNotFound(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
        }
    }

    public static final class Generic extends SatchelException {
        private static final String PREAMBLE =
                "An unknown exception has occurred in Satchel: ";

        public Generic(String message) {
            super(PREAMBLE + message);
        }

        public Generic(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
        }
    }

    public static final class JigNotFound extends SatchelException {
        private static final String PREAMBLE =
                "No jig was found: ";

        public JigNotFound(String message) {
            super(PREAMBLE + message);
        }

        public JigNotFound(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
        }
    }

    /**
     * Thrown when code touches Satchel-dependent state before {@code Satchel.isReady()} for the
     * calling side. On the client this specifically means: before the world-identity token
     * (RM_SAT_019) has been received and bound. Deliberately distinct from {@link ScopeNotReady}
     * (a specific, already-known scope hasn't converged to LOADED yet) -- this is "there is no
     * valid scope identity to even ask about yet," a precondition failure, not a lifecycle-phase
     * one. Callers that can run before readiness (rendering, commands, anything outside
     * Satchel's own ingress) are expected to check {@code Satchel.isReady()} proactively and skip
     * gracefully; this exception is the safety net for the ones that don't.
     */
    public static final class NotReady extends SatchelException {
        private static final String PREAMBLE =
                "Satchel is not ready yet: ";

        public NotReady(String message) {
            super(PREAMBLE + message);
        }

        public NotReady(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
        }
    }

    public static final class BadLogicalSide extends SatchelException {
        private static final String PREAMBLE =
                "Unexpected logical side.";

        public BadLogicalSide(String message) {
            super(PREAMBLE + message);
        }

        public BadLogicalSide(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
        }
    }
}
