package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerResolver;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.jig.player.PlayerScopeCoupler;
import net.minecraft.server.level.ServerPlayer;

/**
 * RM_SAT_020: the config-side half of the {@code PlayerJig} rebuild, mirroring
 * {@link LevelJigConfig}'s four-category-lens shape exactly. No real consumer registers this yet
 * -- {@code RM_FRO_006} (per-player border evaluation) is the first one planned, and will
 * instantiate a {@code PlayerJigConfig}, attach its own bundle schema and event handlers (the
 * same two per-module calls {@code TrackingModule.init()}/{@code BorderModule.init()} make against
 * a {@code LevelJigConfig}), and call {@code Satchel.registerJigConfig(config)}. Until then this
 * class compiles and is directly constructable, but nothing in either repo instantiates it.
 */
public class PlayerJigConfig extends JigConfig<PlayerScope, ServerPlayer> {

    private final Binding binding = new Binding();
    private final Bundles bundles = new Bundles();
    private final Policies policies = new Policies();
    private final Execution execution = new Execution();

    public PlayerJigConfig(JigKey<? extends SatchelJig<PlayerScope>> key) {
        super(key);
    }

    @Override
    protected Presets<PlayerScope, ServerPlayer> createPresets() {

        Presets<PlayerScope, ServerPlayer> p = new Presets<>();

        // -------------------------------------------------
        // Binding (identity-level)
        // -------------------------------------------------
        p.binding.jigType = PlayerJig.class; // REQUIRED -- must be set by author
        p.binding.couplerClass = PlayerScopeCoupler.class; // REQUIRED -- must be set by author
        p.binding.scopeType = PlayerScope.class;
        p.binding.sourceType = ServerPlayer.class;
        // Server-only by default -- unlike LevelJigConfig's SERVER default, which Border
        // overrides to BOTH for its own client-rendering needs, every real PlayerJig consumer
        // named so far (RM_FRO_006) is server-authoritative, matching Border's own rule-
        // evaluation split (see the Border wiki page's "Runtime wiring" section). A future
        // client-side consumer can still override this the same way Border overrides
        // LevelJigConfig's default.
        p.binding.sideApplicability = JigPolicies.SideApplicability.SERVER;
        p.binding.scopeResolver = PlayerResolver::resolveScope;
        p.binding.uuidDeterminer = PlayerResolver::determineUUID;
        // No referenceLevel default, deliberately -- unlike LevelScope, a PlayerScope has no
        // single Level it's intrinsically tied to (that's precisely why this jig kind exists
        // instead of hosting this state on BordersBundle -- see RM_SAT_020's design log). A
        // consumer whose bundle declares requiresPersistence/requiresNetworking/requiresClock
        // must supply its own referenceLevelResolver (e.g. scope -> scope.player().serverLevel())
        // via .binding().referenceLevel(...) -- left unset here, the same "no default, consumer's
        // job" gap LevelJigConfig had for referenceLevel before SAT_023 added one, except here
        // there's no single correct default to fall back to, so this isn't a bug to fix later.

        // -------------------------------------------------
        // Execution
        // -------------------------------------------------
        p.execution.lifecycle = PlayerJigLifecycle.VALUE;
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
        // Bundles (left for the real consumer, same as LevelJigConfig)
        // -------------------------------------------------
        // p.bundles.schema = ... -- a consumer must call .bundles().schema(...) before
        // registering; JigConfigValidator rejects a config with no schema.

        return p;
    }

    @Override
    public JigBindingConfig<PlayerScope, ServerPlayer> binding() {
        return binding;
    }

    @Override
    public JigBundlesConfig<PlayerScope> bundles() {
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
            extends JigBindingConfig<PlayerScope, ServerPlayer> {

        @Override
        protected Presets<PlayerScope, ServerPlayer> presets() {
            return PlayerJigConfig.this.presets().binding;
        }
    }

    private final class Bundles
            extends JigBundlesConfig<PlayerScope> {

        @Override
        protected Presets<PlayerScope> presets() {
            return PlayerJigConfig.this.presets().bundles;
        }
    }

    private final class Policies
            extends JigPoliciesConfig {

        @Override
        protected Presets presets() {
            return PlayerJigConfig.this.presets().policies;
        }
    }

    private final class Execution
            extends JigExecutionConfig {

        @Override
        protected Presets presets() {
            return PlayerJigConfig.this.presets().execution;
        }
    }

    public static final class PlayerJigLifecycle {

        public static final JigPolicies.Lifecycle VALUE =
                JigPolicies.Lifecycle.defaults()
                        .withTick(true)
                        .withExecutionPulse(false);
    }
}
