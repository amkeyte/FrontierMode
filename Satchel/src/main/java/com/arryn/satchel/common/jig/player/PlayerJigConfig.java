package com.arryn.satchel.common.jig.player;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.config.*;
import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerResolver;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.jig.player.PlayerScopeCoupler;
import net.minecraft.world.entity.player.Player;

/**
 * RM_SAT_020: the config-side half of the {@code PlayerJig} rebuild, mirroring
 * {@link LevelJigConfig}'s four-category-lens shape exactly. Real consumers: FrontierMode's
 * {@code BorderModule} (per-player border evaluation) and Satchel's own {@code
 * PlayerHealthCheck}. Source type widened from {@code ServerPlayer} to the abstract {@link
 * Player} by SAT_063 -- see {@link PlayerScope}'s own class doc for why; {@code
 * sideApplicability} still defaults to {@code SERVER} below, same as {@link LevelJigConfig}'s
 * own default, for the same reason: a consumer opts into {@code BOTH} explicitly when it
 * actually wants the client-side path (see {@code PlayerHealthCheck.register()} for the first
 * real consumer doing exactly that).
 */
public class PlayerJigConfig extends JigConfig<PlayerScope, Player> {

    private final Binding binding = new Binding();
    private final Bundles bundles = new Bundles();
    private final Policies policies = new Policies();
    private final Execution execution = new Execution();

    public PlayerJigConfig(JigKey<? extends SatchelJig<PlayerScope>> key) {
        super(key);
    }

    @Override
    protected Presets<PlayerScope, Player> createPresets() {

        Presets<PlayerScope, Player> p = new Presets<>();

        // -------------------------------------------------
        // Binding (identity-level)
        // -------------------------------------------------
        p.binding.jigType = PlayerJig.class; // REQUIRED -- must be set by author
        p.binding.couplerClass = PlayerScopeCoupler.class; // REQUIRED -- must be set by author
        p.binding.scopeType = PlayerScope.class;
        p.binding.sourceType = Player.class;
        // SERVER by default, same as LevelJigConfig's own default -- a consumer that actually
        // wants the client-side path (SAT_063 made that real) opts into BOTH explicitly, the
        // same way Border overrides LevelJigConfig's own SERVER default for its own
        // client-rendering needs.
        p.binding.sideApplicability = JigPolicies.SideApplicability.SERVER;
        p.binding.scopeResolver = PlayerResolver::resolveScope;
        p.binding.uuidDeterminer = PlayerResolver::determineUUID;
        // No referenceLevel default, deliberately -- unlike LevelScope, a PlayerScope has no
        // single Level it's intrinsically tied to (that's precisely why this jig kind exists
        // instead of hosting this state on BordersBundle -- see RM_SAT_020's design log). A
        // consumer whose bundle declares Capability.PERSISTENCE/NETWORKING/CLOCK in its own
        // required() (SAT_053) must ALSO set this jig's capabilities().allowsPersistence()/
        // allowsNetworking()/allowsClock() accordingly (the engine throws if the bundle asks for
        // something this jig doesn't allow), and must supply its own referenceLevelResolver via
        // .binding().referenceLevel(...) -- left unset here, the same "no default, consumer's
        // job" gap LevelJigConfig had for referenceLevel before SAT_023 added one, except here
        // there's no single correct default to fall back to, so this isn't a bug to fix later.
        // referenceLevelResolver is only ever consulted server-side (ScopeEngine_Server), so a
        // consumer's own resolver can safely cast down, e.g.
        // scope -> ((ServerPlayer) scope.player()).serverLevel().

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
    public JigBindingConfig<PlayerScope, Player> binding() {
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
            extends JigBindingConfig<PlayerScope, Player> {

        @Override
        protected Presets<PlayerScope, Player> presets() {
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
