package com.arryn.satchel.common.jig.config;

import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.trinket.TrinketDecl;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

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

    /**
     * The Trinkets this jig declares, in declaration order. Empty if none.
     */
    public List<TrinketDecl<?>> trinkets() {
        return Collections.unmodifiableList(presets().trinkets);
    }

    /* =============================================================
     * Mutators (fluent)
     * ========================================================== */

    public JigBundlesConfig<S> schema(JigBundles.Schema<S> schema) {
        presets().schema = schema;
        return this;
    }

    /**
     * Declares a {@link Trinket}: Satchel calls {@code factory} once per scope when the scope loads
     * and drops the instance at unload. One declaration per type; declaration order is attach
     * order. Checked at compile time of the config, not here.
     */
    public <T extends Trinket> JigBundlesConfig<S> trinket(Class<T> type, Supplier<? extends T> factory) {
        presets().trinkets.add(new TrinketDecl<>(type, factory));
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
        public JigBundles.Schema<S> schema;

        /**
         * Declared Trinkets, in declaration order. Optional.
         */
        public final List<TrinketDecl<?>> trinkets = new java.util.ArrayList<>();
    }
}
