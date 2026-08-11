package com.arryn.satchel.common.newconfig.newnew;

public final class JigPolicies {

    private JigPolicies() {}

    // ───────────────── enums ─────────────────

    public enum CreatePolicy {
        ALWAYS, ON_DEMAND, NEVER
    }

    public enum ExecutionPriority {
        LOW, NORMAL, HIGH
    }

    public enum TickOrder {
        UNORDERED, BEFORE_BUNDLES, AFTER_BUNDLES
    }

    public enum SideApplicability {
        CLIENT, SERVER, BOTH
    }

    public enum ReadinessMode {
        IMMEDIATE, DEFERRED
    }

    public enum SyncMode {
        NONE, SERVER_TO_CLIENT
    }

    public enum FlushPolicy {
        NONE, EXECUTION_PULSE, UNLOAD
    }

    public enum MissingDataPolicy {
        IGNORE, WARN, ERROR
    }

    public enum ViolationPolicy {
        THROW, WARN, IGNORE
    }

    // ───────────────── records ─────────────────

    public record Lifecycle(
            boolean participatesInLoad,
            boolean participatesInUnload,
            boolean participatesInTick,
            boolean participatesInExecutionPulse
    ) {
        public static Lifecycle defaults() {
            return new Lifecycle(true, true, false, false);
        }

        public Lifecycle withLoad(boolean v) {
            return new Lifecycle(
                    v,
                    participatesInUnload,
                    participatesInTick,
                    participatesInExecutionPulse
            );
        }

        public Lifecycle withUnload(boolean v) {
            return new Lifecycle(
                    participatesInLoad,
                    v,
                    participatesInTick,
                    participatesInExecutionPulse
            );
        }

        public Lifecycle withTick(boolean v) {
            return new Lifecycle(
                    participatesInLoad,
                    participatesInUnload,
                    v,
                    participatesInExecutionPulse
            );
        }

        public Lifecycle withExecutionPulse(boolean v) {
            return new Lifecycle(
                    participatesInLoad,
                    participatesInUnload,
                    participatesInTick,
                    v
            );
        }
    }


    public record Readiness(
            ReadinessMode mode,
            boolean requiresReadyForAccess,
            boolean requiresReadyForCreate,
            boolean requiresReadyForTick,
            boolean requiresReadyForSync,
            boolean allowPreReadyBundles,
            boolean allowPreReadyAccess
    ) {
        public static Readiness defaults() {
            return new Readiness(
                    ReadinessMode.IMMEDIATE,
                    false, false, false, false,
                    true, true
            );
        }
    }

    public record Sync(
            SyncMode mode,
            Integer intervalTicks,
            boolean syncOnDirty,
            boolean syncOnTick,
            boolean tolerateUnknownBundles,
            boolean tolerateOutOfOrder
    ) {
        public static Sync defaults() {
            return new Sync(
                    SyncMode.NONE,
                    null,
                    false,
                    false,
                    true,
                    true
            );
        }
    }

    public record Persistence(
            boolean persistent,
            FlushPolicy flushOn,
            MissingDataPolicy missingPolicy
    ) {
        public static Persistence defaults() {
            return new Persistence(false, FlushPolicy.NONE, MissingDataPolicy.IGNORE);
        }
    }

    public record Errors(
            ViolationPolicy onAccessViolation,
            ViolationPolicy onMissingBundle,
            ViolationPolicy onHydrationFailure,
            ViolationPolicy onSyncFailure
    ) {
        public static Errors defaults() {
            return new Errors(
                    ViolationPolicy.THROW,
                    ViolationPolicy.IGNORE,
                    ViolationPolicy.WARN,
                    ViolationPolicy.WARN
            );
        }
    }

    public record Capabilities(
            boolean requiresPersistence,
            boolean requiresNetworking,
            boolean requiresClock
    ) {
        public static Capabilities defaults() {
            return new Capabilities(false, false, false);
        }
    }

    public record Diagnostics(
            boolean enableTracing,
            boolean emitLifecycleEvents,
            boolean emitMetrics
    ) {
        public static Diagnostics defaults() {
            return new Diagnostics(false, false, false);
        }
    }
}
