package com.arryn.satchel.common.jig.config;

import com.arryn.satchel.common.lifecycle.EventHandlers;

/**
 * Execution and lifecycle participation configuration for a Jig.
 *
 * <p>
 * Controls *when* a jig participates in execution phases and
 * which lifecycle handlers are attached.
 */
public abstract class JigExecutionConfig {

    protected JigExecutionConfig() {}

    /* =============================================================
     * Accessors
     * ========================================================== */

    public JigPolicies.Lifecycle lifecycle() {
        return presets().lifecycle;
    }

    public EventHandlers eventHandlers() {
        return presets().eventHandlers;
    }

    /* =============================================================
     * Mutators (fluent)
     * ========================================================== */

    public JigExecutionConfig lifecycle(JigPolicies.Lifecycle lifecycle) {
        presets().lifecycle = lifecycle;
        return this;
    }

    /**
     * Directly assign fully-constructed event handlers.
     */
    public JigExecutionConfig eventHandlers(EventHandlers handlers) {
        presets().eventHandlers = handlers;
        return this;
    }

    /**
     * Convenience builder for inline handler declaration.
     *
     * <p>
     * The builder is transient; calling {@code build()} installs
     * the resulting {@link EventHandlers} into this config.
     */
    public EventHandlers.Builder eventHandlersBuilder() {
        return new EventHandlers.Builder() {
            @Override
            public EventHandlers build() {
                EventHandlers built = super.build();
                presets().eventHandlers = built;
                return built;
            }
        };
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
         * Declares which lifecycle phases this jig participates in.
         */
        public JigPolicies.Lifecycle lifecycle;

        /**
         * Finalized lifecycle handlers (optional).
         */
        public EventHandlers eventHandlers;
    }
}
