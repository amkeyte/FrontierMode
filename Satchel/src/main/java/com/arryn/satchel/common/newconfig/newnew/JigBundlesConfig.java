package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.newconfig.JigBundles;

/**
 * Bundle schema configuration for a Jig.
 *
 * <p>
 * Responsible for declaring the bundle schema that a jig
 * exposes and consumes.
 *
 * <p>
 * This is a pure configuration category:
 * <ul>
 *   <li>No lifecycle logic</li>
 *   <li>No persistence logic</li>
 *   <li>No engine coupling</li>
 * </ul>
 */
public abstract class JigBundlesConfig<S extends SatchelScope> {

    protected JigBundlesConfig() {}

    /* =============================================================
     * Accessors
     * ========================================================== */

    public JigBundles.Schema<S> schema() {
        return presets().schema;
    }

    /* =============================================================
     * Mutators (fluent)
     * ========================================================== */

    public JigBundlesConfig<S> schema(JigBundles.Schema<S> schema) {
        presets().schema = schema;
        return this;
    }

    /* =============================================================
     * Preset access
     * ========================================================== */

    protected abstract Presets<S> presets();

    /* =============================================================
     * Preset node (owned by JigConfig)
     * ========================================================== */

    public static class Presets<S extends SatchelScope> {

        /**
         * Bundle schema exposed by this jig.
         * Required.
         */
        JigBundles.Schema<S> schema;
    }
}
