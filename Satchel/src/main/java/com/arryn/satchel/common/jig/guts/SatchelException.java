package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.LogicalSide;

/**
 * Base exception for requireJig-related access failures.
 * <p>
 * This hierarchy distinguishes between:
 * - AccessDenied: the operation is not permitted
 * - AccessFailed: the operation was permitted but could not complete
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

    /* =============================================================
     * Access denied (policy / lifecycle violation)
     * ========================================================== */

    /**
     * Thrown when an operation is not permitted due to
     * lifecycle state, logical side, or requireJig policy.
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
            super(PREAMBLE + message);
        }

        public BundleNotFound(String message, Throwable cause) {
            super(PREAMBLE + message, cause);
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
                "No Jig scopeInfo was found: ";

        public JigNotFound(String message) {
            super(PREAMBLE + message);
        }

        public JigNotFound(String message, Throwable cause) {
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
