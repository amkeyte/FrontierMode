package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.jig.mob.MobScopeCoupler;
import net.minecraft.world.entity.Mob;

/**
 * The config-side half of the {@code MobJig} build (RM_SAT_021, "Frank"), mirroring
 * {@link PlayerJigConfig}'s four-category-lens shape structurally, with one deliberate
 * departure: this class ships <b>no default {@code sideApplicability}</b>. Unlike
 * {@link LevelJigConfig}'s {@code SERVER} default (which Border overrides to {@code BOTH} for
 * its own client-rendering needs) or {@link PlayerJigConfig}'s own {@code SERVER} default,
 * Satchel itself shouldn't assume mob-scoped state is forever server-only just because the first
 * named consumer (Boss, defeat-detection) happens to be. Each consumer's own {@code MobJigConfig}
 * instance must call {@code .binding().sideApplicability(...)} explicitly before registering --
 * {@link JigConfigValidator} throws loudly at compile time if it's left unset, by design, not as
 * a bug to route around.
 */
public class MobJigConfig extends JigConfig<MobScope, Mob> {

    private final Binding binding = new Binding();
    private final Bundles bundles = new Bundles();
    private final Policies policies = new Policies();
    private final Execution execution = new Execution();

    public MobJigConfig(JigKey<? extends SatchelJig<MobScope>> key) {
        super(key);
    }

    @Override
    protected Presets<MobScope, Mob> createPresets() {

        Presets<MobScope, Mob> p = new Presets<>();

        // -------------------------------------------------
        // Binding (identity-level)
        // -------------------------------------------------
        p.binding.jigType = MobJig.class; // REQUIRED -- must be set by author
        p.binding.couplerClass = MobScopeCoupler.class; // REQUIRED -- must be set by author
        p.binding.scopeType = MobScope.class;
        p.binding.sourceType = Mob.class;
        // sideApplicability intentionally left unset -- see class docs. JigConfigValidator
        // throws IllegalStateException("Jig ... missing sideApplicability") if a consumer forgets
        // to set it before registering.
        p.binding.scopeResolver = MobScope::resolveScope;
        p.binding.uuidDeterminer = MobScope::resolveUUID;
        // No referenceLevel default, same gap PlayerJigConfig leaves open and for the same
        // reason -- a MobScope has no single Level it's intrinsically tied to independent of
        // which entity it wraps. A consumer whose bundle declares
        // requiresPersistence/requiresNetworking/requiresClock must supply its own
        // referenceLevelResolver via .binding().referenceLevel(...).

        // -------------------------------------------------
        // Execution
        // -------------------------------------------------
        p.execution.lifecycle = MobJigLifecycle.VALUE;
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
        // Bundles (left for the real consumer, same as LevelJigConfig/PlayerJigConfig)
        // -------------------------------------------------
        // p.bundles.schema = ... -- a consumer must call .bundles().schema(...) before
        // registering; JigConfigValidator rejects a config with no schema.

        return p;
    }

    @Override
    public JigBindingConfig<MobScope, Mob> binding() {
        return binding;
    }

    @Override
    public JigBundlesConfig<MobScope> bundles() {
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
            extends JigBindingConfig<MobScope, Mob> {

        @Override
        protected Presets<MobScope, Mob> presets() {
            return MobJigConfig.this.presets().binding;
        }
    }

    private final class Bundles
            extends JigBundlesConfig<MobScope> {

        @Override
        protected Presets<MobScope> presets() {
            return MobJigConfig.this.presets().bundles;
        }
    }

    private final class Policies
            extends JigPoliciesConfig {

        @Override
        protected Presets presets() {
            return MobJigConfig.this.presets().policies;
        }
    }

    private final class Execution
            extends JigExecutionConfig {

        @Override
        protected Presets presets() {
            return MobJigConfig.this.presets().execution;
        }
    }

    public static final class MobJigLifecycle {

        public static final JigPolicies.Lifecycle VALUE =
                JigPolicies.Lifecycle.defaults()
                        .withTick(true)
                        .withExecutionPulse(false);
    }
}
