package com.arryn.satchel.common.jig.config;

/**
 * Container for the declarative policy records a {@link JigPoliciesConfig} exposes per
 * category -- {@link Lifecycle}, {@link Readiness}, {@link Sync}, {@link Persistence},
 * {@link Errors}, {@link Diagnostics}, {@link Capabilities} -- plus the small enums those
 * records are built from.
 *
 * <p>
 * These records describe constraints and guarantees a jig author declares, not behavior
 * the records themselves implement. Whether a given field actually changes engine behavior
 * depends entirely on whether something downstream reads it -- and per SAT_054's field-by-
 * field audit (2026-09-10), most of them currently don't. Each record's own javadoc below
 * says exactly which of its fields are read today and which are declared but inert, so a
 * consumer setting a field can tell whether they're describing real behavior or writing
 * down an intention nothing enforces yet. See SAT_054 for the live audit and its boss-
 * secrecy motivating case for why "unread today" isn't the same as "not worth having."
 */
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

    //reserved as future for when client networking requests are built out.
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

    /**
     * When this jig's execution engine calls in -- the one policy record in this class
     * that's fully load-bearing (SAT_054): all four fields are read by the jig/engine
     * machinery to decide whether to fire load, unload, per-tick, and execution-pulse
     * callbacks. {@code participatesInTick} and {@code participatesInExecutionPulse} are
     * independent flags on two different clocks, not synonyms -- a jig set to tick-only
     * stays invisible to anything gated on execution-pulse participation (see
     * SatchelHealth's own jig configs, which set {@code withTick(true)} but never
     * {@code withExecutionPulse(true)}, and the RM_SAT_013 health-check gap that came from
     * it).
     */
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


    /**
     * Declares when a scope is considered "ready" and what's allowed to happen before it
     * is. Real, but mostly unwired today (SAT_054): only {@link #mode()} is actually read
     * ({@code AScopeCoupler}, {@code ScopeInfo}). The other five fields --
     * {@code requiresReadyForAccess/Create/Tick/Sync}, {@code allowPreReadyBundles},
     * {@code allowPreReadyAccess} -- are declared here and carried through config, but
     * nothing in either repo currently reads them; they describe the shape a real
     * coupler-readiness gate would need, not behavior that exists yet. This is the
     * config-side half of the same gap {@link com.arryn.satchel.common.jig.guts.ScopeCoupler}'s
     * before/after hooks are the code-side half of -- see that interface's javadoc for the
     * concrete example (a future {@code BossMobScopeCoupler} answering "is this mob
     * actually materialized," not just "does the entity resolve").
     */
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

    /**
     * Declares how a bundle's data is expected to synchronize to clients -- mode,
     * interval, dirty/tick-triggered sync, tolerance for unknown or out-of-order data.
     * Currently fully declarative: SAT_054's audit found zero reads of any of these six
     * fields anywhere in either repo. The server-to-client sync path that exists today
     * (bundle parcels, {@code SatchelBundle#pulseSync}) runs unconditionally rather than
     * being driven by this record. Setting these fields records intent for a future sync
     * engine, not a lever that changes behavior right now.
     */
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

    /**
     * Declares whether a bundle persists and how. Partially wired: {@link #persistent()}
     * is the one field actually read ({@code JigConfigValidator}'s cross-check against a
     * bundle's {@code Capability.PERSISTENCE}, SAT_053). {@link #flushOn()} and
     * {@link #missingPolicy()} are declared by real consumers -- BorderModule sets both,
     * by its own comment, "so the policy reads as intentional" -- but read by nothing;
     * flush timing and missing-data handling are both currently hardcoded in
     * {@code ScopeEngine_Server} rather than driven by this record. See SAT_054.
     */
    public record Persistence(
            boolean persistent,
            FlushPolicy flushOn,
            MissingDataPolicy missingPolicy
    ) {
        public static Persistence defaults() {
            return new Persistence(false, FlushPolicy.NONE, MissingDataPolicy.IGNORE);
        }
    }

    /**
     * Declares how each failure mode (access violation, missing bundle, hydration
     * failure, sync failure) should be handled -- throw, warn, or ignore. Currently fully
     * declarative: SAT_054's audit found zero reads of any of these four fields anywhere
     * in either repo. Actual failure handling for each of these cases is hardcoded at its
     * respective call site today, independent of whatever this record says.
     */
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

    /**
     * Declares tracing/metrics/lifecycle-event verbosity for a jig. Partially wired:
     * {@link #enableTracing()} is read (three call sites in {@code ASatchelJig}, each just
     * guarding a trace log). {@link #emitLifecycleEvents()} and {@link #emitMetrics()} are
     * declared but read by nothing -- no lifecycle-event or metrics emission is currently
     * gated on either flag. See SAT_054.
     */
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
