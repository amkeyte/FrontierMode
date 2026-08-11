package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.guts.SatchelScope;

/**
 * Declarative configuration describing how a Jig operates.
 *
 * <p>
 * This class owns the preset tree and exposes category lenses.
 * It contains no engine logic, no lifecycle execution, and no validation.
 *
 * Categories may be migrated out of the top-level config incrementally.
 */
public abstract class JigConfig<S extends SatchelScope, SRC> {

    private final JigKey<? extends SatchelJig<S>> jigKey;
    final Presets<S, SRC> presets;

    protected JigConfig(JigKey<? extends SatchelJig<S>> jigKey) {
        this.jigKey = jigKey;
        this.presets = createPresets();
    }

    /**
     * Subclasses create and specialize their preset tree here.
     * Called exactly once during construction.
     */
    protected abstract Presets<S, SRC> createPresets();

    /* =============================================================
     * Identity
     * ========================================================== */

    public final JigKey<? extends SatchelJig<S>> jigKey() {
        return jigKey;
    }

    protected final Presets<S, SRC> presets() {
        return presets;
    }

    /* =============================================================
     * Top-level fields (not yet categorized)
     * ========================================================== */

    public Class<? extends SatchelJig<S>> jigType() {
        return presets.jigType;
    }

    public Class<S> scopeType() {
        return presets.scopeType;
    }

    public Class<SRC> sourceType() {
        return presets.sourceType;
    }

    /* =============================================================
     * Category accessors
     * ========================================================== */

    // These are intentionally abstract so jig kinds decide
    // which categories they expose.

    public abstract JigBindingConfig<S, SRC> binding();

    public abstract JigBundlesConfig<S> bundles();

    public abstract JigPoliciesConfig policies();

    public abstract JigExecutionConfig execution();

    /* =============================================================
     * Preset root (single instance per config)
     * ========================================================== */

    public static class Presets<S extends SatchelScope, SRC> {

        /* -----------------------------
         * Identity (required)
         * -------------------------- */

        Class<? extends SatchelJig<S>> jigType;
        Class<S> scopeType;
        Class<SRC> sourceType;

        /* -----------------------------
         * Category preset nodes
         * -------------------------- */

        final JigBindingConfig.Presets<S, SRC> binding =
                new JigBindingConfig.Presets<>();

        final JigBundlesConfig.Presets<S> bundles =
                new JigBundlesConfig.Presets<>();

        final JigPoliciesConfig.Presets policies =
                new JigPoliciesConfig.Presets();

        final JigExecutionConfig.Presets execution =
                new JigExecutionConfig.Presets();
    }
}
