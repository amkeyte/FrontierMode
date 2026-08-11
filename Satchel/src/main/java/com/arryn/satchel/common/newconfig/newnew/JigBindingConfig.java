package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.jig.guts.ScopeCoupler;
import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import net.minecraft.world.level.Level;

import java.util.function.Function;

/**
 * Identity and scope binding configuration for a Jig.
 *
 * <p>
 * Responsible for:
 * <ul>
 *   <li>Declaring jig implementation type</li>
 *   <li>Declaring coupler implementation type</li>
 *   <li>Declaring scope and source types</li>
 *   <li>Declaring logical side applicability</li>
 *   <li>Deriving scopes from source objects</li>
 *   <li>Deriving stable UUIDs for scopes</li>
 * </ul>
 *
 * <p>
 * This class is a category lens over shared preset state.
 * It owns no data and performs no validation.
 */
public abstract class JigBindingConfig<S extends SatchelScope, SRC> {

    protected JigBindingConfig() {}

    /* =============================================================
     * Accessors
     * ========================================================== */

    public Class<? extends SatchelJig<S>> jigType() {
        return presets().jigType;
    }

    public Class<? extends ScopeCoupler> couplerType() {
        return presets().couplerClass;
    }

    public Class<S> scopeType() {
        return presets().scopeType;
    }

    public Class<SRC> sourceType() {
        return presets().sourceType;
    }

    public JigPolicies.SideApplicability sideApplicability() {
        return presets().sideApplicability;
    }

    public JigBinding.ScopeResolver<S> scopeResolver() {
        return presets().scopeResolver;
    }

    public JigBinding.UuidDerterminer<SRC> uuidDeriver() {
        return presets().uuidDeterminer;
    }

    public Function<S, Level> referenceLevelResolver(){return presets().referenceLevel;}


    /* =============================================================
     * Mutators (fluent)
     * ========================================================== */

    public JigBindingConfig<S, SRC> jigType(
            Class<? extends SatchelJig<S>> type
    ) {
        presets().jigType = type;
        return this;
    }

    public JigBindingConfig<S, SRC> couplerType(
            Class<? extends ScopeCoupler> type
    ) {
        presets().couplerClass = type;
        return this;
    }

    public JigBindingConfig<S, SRC> scopeType(
            Class<S> type
    ) {
        presets().scopeType = type;
        return this;
    }

    public JigBindingConfig<S, SRC> sourceType(
            Class<SRC> type
    ) {
        presets().sourceType = type;
        return this;
    }

    public JigBindingConfig<S, SRC> sideApplicability(
            JigPolicies.SideApplicability side
    ) {
        presets().sideApplicability = side;
        return this;
    }

    public JigBindingConfig<S, SRC> scopeResolver(
            JigBinding.ScopeResolver<S> resolver
    ) {
        presets().scopeResolver = resolver;
        return this;
    }

    public JigBindingConfig<S, SRC> UUIDDeterminer(
            JigBinding.UuidDerterminer<SRC> deriver
    ) {
        presets().uuidDeterminer = deriver;
        return this;
    }

    /* =============================================================
     * Preset access
     * ========================================================== */

    protected abstract Presets<S, SRC> presets();

    /* =============================================================
     * Preset node (owned by JigConfig)
     * ========================================================== */

    public static class Presets<S extends SatchelScope, SRC> {

        /**
         * Concrete jig implementation type.
         * Required.
         */
        public Class<? extends SatchelJig<S>> jigType;

        /**
         * Concrete coupler implementation type.
         * Required.
         */
        public Class<? extends ScopeCoupler> couplerClass;

        /**
         * Concrete scope type.
         * Required.
         */
        public Class<S> scopeType;

        /**
         * Concrete source type.
         * Required.
         */
        public Class<SRC> sourceType;

        /**
         * Logical side applicability.
         * Required.
         */
        public JigPolicies.SideApplicability sideApplicability;

        /**
         * Resolves a scope instance from an opaque source object.
         * Required.
         */
        public JigBinding.ScopeResolver<S> scopeResolver;

        /**
         * Derives a determinant UUID for a scope from its source.
         * Required unless scopes are ephemeral.
         */
        public JigBinding.UuidDerterminer<SRC> uuidDeterminer;
        public Function<S, Level> referenceLevel;
    }
}
