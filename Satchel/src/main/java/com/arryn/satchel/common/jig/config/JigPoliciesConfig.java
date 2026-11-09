package com.arryn.satchel.common.jig.config;

/**
 * Policy configuration for a Jig.
 *
 * <p>
 * Policies describe *constraints and guarantees*, not behavior:
 * <ul>
 *   <li>Side applicability</li>
 *   <li>Readiness</li>
 *   <li>Synchronization</li>
 *   <li>Persistence</li>
 *   <li>Error handling</li>
 *   <li>Diagnostics</li>
 *   <li>Capabilities</li>
 * </ul>
 *
 * <p>
 * This class is a category lens over shared preset state.
 * It owns no logic and performs no validation.
 */
public abstract class JigPoliciesConfig {

    protected JigPoliciesConfig() {}

    /* =============================================================
     * Accessors
     * ========================================================== */

    public boolean enabled() {
        return presets().enabled;
    }

    public JigPolicies.SideApplicability sideApplicability() {
        return presets().sideApplicability;
    }

    public JigPolicies.Readiness readiness() {
        return presets().readiness;
    }

    public JigPolicies.Sync sync() {
        return presets().sync;
    }

    public JigPolicies.Persistence persistence() {
        return presets().persistence;
    }

    public JigPolicies.Errors errors() {
        return presets().errors;
    }

    public JigPolicies.Diagnostics diagnostics() {
        return presets().diagnostics;
    }

    public JigPolicies.Capabilities capabilities() {
        return presets().capabilities;
    }

    /**
     * Mob jigs only: how many mobs the jig may hold interest in at once. Exceeding it crashes in a
     * development run and warns in release, naming the jig and a few UUIDs. It is the diagnostic for
     * a module that forgets {@code lostInterest}. Default {@value #DEFAULT_MAX_INTEREST}.
     */
    public int maxInterest() {
        return presets().maxInterest;
    }

    /** The default of {@link #maxInterest()}. */
    public static final int DEFAULT_MAX_INTEREST = 500;

    /* =============================================================
     * Mutators (fluent)
     * ========================================================== */

    /**
     * Sets this mob jig's interest cap; see {@link #maxInterest()}.
     */
    public JigPoliciesConfig maxInterest(int max) {
        if (max < 1) throw new IllegalArgumentException("maxInterest must be at least 1, got " + max);
        presets().maxInterest = max;
        return this;
    }

    public JigPoliciesConfig enabled(boolean enabled) {
        presets().enabled = enabled;
        return this;
    }

    public JigPoliciesConfig sideApplicability(
            JigPolicies.SideApplicability side
    ) {
        presets().sideApplicability = side;
        return this;
    }

    public JigPoliciesConfig readiness(JigPolicies.Readiness policy) {
        presets().readiness = policy;
        return this;
    }

    public JigPoliciesConfig sync(JigPolicies.Sync policy) {
        presets().sync = policy;
        return this;
    }

    public JigPoliciesConfig persistence(JigPolicies.Persistence policy) {
        presets().persistence = policy;
        return this;
    }

    public JigPoliciesConfig errors(JigPolicies.Errors policy) {
        presets().errors = policy;
        return this;
    }

    public JigPoliciesConfig diagnostics(JigPolicies.Diagnostics policy) {
        presets().diagnostics = policy;
        return this;
    }

    public JigPoliciesConfig capabilities(JigPolicies.Capabilities policy) {
        presets().capabilities = policy;
        return this;
    }

    /* =============================================================
     * Preset access
     * ========================================================== */

    protected abstract Presets presets();

    /* =============================================================
     * Preset node (owned by JigConfig)
     * ========================================================== */

    public static class Presets {

        /**
         * Whether this jig is enabled at all.
         */
        public boolean enabled = true;

        /**
         * Which logical side(s) this jig applies to.
         */
        public JigPolicies.SideApplicability sideApplicability;

        /**
         * Readiness policy (when a scope is considered usable).
         */
        public JigPolicies.Readiness readiness;

        /**
         * Synchronization policy.
         */
        public JigPolicies.Sync sync;

        /**
         * Persistence policy.
         */
        public JigPolicies.Persistence persistence;

        /**
         * Error handling policy.
         */
        public JigPolicies.Errors errors;

        /**
         * Diagnostic / logging policy.
         */
        public JigPolicies.Diagnostics diagnostics;

        /**
         * Declared capabilities (clock, networking, persistence, etc.).
         */
        public JigPolicies.Capabilities capabilities;

        /**
         * Mob jigs only: the interest cap.
         */
        public int maxInterest = DEFAULT_MAX_INTEREST;
    }
}
