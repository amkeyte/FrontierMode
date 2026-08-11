package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.newconfig.EventHandlers;

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

    public JigPolicies.ExecutionPriority priority() {
        return presets().executionPriority;
    }

    public JigPolicies.TickOrder tickOrder() {
        return presets().tickOrder;
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

    public JigExecutionConfig executionPriority(
            JigPolicies.ExecutionPriority priority
    ) {
        presets().executionPriority = priority;
        return this;
    }

    public JigExecutionConfig tickOrder(JigPolicies.TickOrder order) {
        presets().tickOrder = order;
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
        JigPolicies.Lifecycle lifecycle;

        /**
         * Execution priority relative to other jigs.
         */
        JigPolicies.ExecutionPriority executionPriority;

        /**
         * Tick ordering constraints.
         */
        JigPolicies.TickOrder tickOrder;

        /**
         * Finalized lifecycle handlers (optional).
         */
        EventHandlers eventHandlers;
    }
}
