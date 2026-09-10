package com.arryn.satchel.common.jig.config;

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

    /**
     * SAT_053 (revised 2026-09-10): what THIS JIG declares supported/allowed, not what any
     * particular bundle needs -- that's {@link com.arryn.satchel.common.bundle.SatchelBundle
     * #required()}, a separate, bundle-author-owned declaration. A bundle whose {@code
     * required()} claims a capability this jig doesn't allow here is a real wiring mistake
     * between the bundle author and the jig author; the engine throws rather than silently
     * skipping (see ScopeEngine_Server#resolveServerLevel). Field names deliberately read as
     * "allows", not "requires" -- the old names described the wrong owner and were the root of
     * the conflation SAT_053 exists to fix (a bundle needing neither capability used to depend
     * on this exact record being true before it could ever leave CREATED).
     */
    public record Capabilities(
            boolean allowsPersistence,
            boolean allowsNetworking,
            boolean allowsClock
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
