package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelResolver;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.level.LevelScopeCoupler;
import net.minecraft.world.level.Level;

public class LevelJigConfig extends JigConfig<LevelScope, Level> {

    private final Binding binding = new Binding();
    private final Bundles bundles = new Bundles();
    private final Policies policies = new Policies();
    private final Execution execution = new Execution();

    public LevelJigConfig(JigKey<? extends SatchelJig<LevelScope>> key) {
        super(key);
    }

    @Override
    protected Presets<LevelScope, Level> createPresets() {

        Presets<LevelScope, Level> p = new Presets<>();

        // -------------------------------------------------
        // Binding (identity-level)
        // -------------------------------------------------
        p.binding.jigType = LevelJig.class; // REQUIRED – must be set by author
        p.binding.couplerClass = LevelScopeCoupler.class; // REQUIRED – must be set by author
        p.binding.scopeType = LevelScope.class;
        p.binding.sourceType = Level.class;
        p.binding.sideApplicability = JigPolicies.SideApplicability.SERVER;
        p.binding.scopeResolver = LevelResolver::resolveScope;
        p.binding.uuidDeterminer = LevelResolver::determineUUID;
        // A LevelScope already holds its own Level directly (see LevelScope.level()) -- no
        // registry lookup needed. Left unset, ScopeInfo.referenceLevel() returns null
        // unconditionally, and any jig that legitimately requires a persistence/networking/clock
        // capability throws AccessFailed("no referenceLevel available") on its very first
        // hydrate, even though the Level was available the whole time. See SAT_023.
        p.binding.referenceLevel = LevelScope::level;

        // -------------------------------------------------
        // Execution
        // -------------------------------------------------
        p.execution.lifecycle = LevelJigLifecycle.VALUE;
        p.execution.executionPriority = JigPolicies.ExecutionPriority.NORMAL;
        p.execution.tickOrder = JigPolicies.TickOrder.UNORDERED;

        // -------------------------------------------------
        // Policies
        // -------------------------------------------------
        p.policies.readiness = JigPolicies.Readiness.defaults();
        p.policies.sync = JigPolicies.Sync.defaults();
        p.policies.persistence = JigPolicies.Persistence.defaults();
        p.policies.errors = JigPolicies.Errors.defaults();
        p.policies.diagnostics = JigPolicies.Diagnostics.defaults();
        p.policies.capabilities = JigPolicies.Capabilities.defaults();

        // -------------------------------------------------
        // Bundles (left for later)
        // -------------------------------------------------
        // p.bundles.schema = LevelBundleSchemas.DEFAULT;

        return p;
    }

    @Override
    public JigBindingConfig<LevelScope, Level> binding() {
        return binding;
    }

    @Override
    public JigBundlesConfig<LevelScope> bundles() {
        return bundles;
    }

    @Override
    public JigPoliciesConfig policies() {
        return policies;
    }

    @Override
    public JigExecutionConfig execution() {
        return execution;
    }

    private final class Binding
            extends JigBindingConfig<LevelScope, Level> {

        @Override
        protected Presets<LevelScope, Level> presets() {
            return LevelJigConfig.this.presets().binding;
        }
    }

    private final class Bundles
            extends JigBundlesConfig<LevelScope> {

        @Override
        protected Presets<LevelScope> presets() {
            return LevelJigConfig.this.presets().bundles;
        }
    }

    private final class Policies
            extends JigPoliciesConfig {

        @Override
        protected Presets presets() {
            return LevelJigConfig.this.presets().policies;
        }
    }

    private final class Execution
            extends JigExecutionConfig {

        @Override
        protected Presets presets() {
            return LevelJigConfig.this.presets().execution;
        }
    }

    public static final class LevelJigLifecycle {

        public static final JigPolicies.Lifecycle VALUE =
                JigPolicies.Lifecycle.defaults()
                        .withTick(true)
                        .withExecutionPulse(false);
    }
}
