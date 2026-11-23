package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.keeper.common.bundle.KeeperBundle;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.ScopeEvent;

import java.util.List;

/**
 * Builds {@code KEEPER_JIG}: a {@code LevelJigConfig} holding {@link KeeperFixture} in {@link KeeperBundle},
 * persisted and networked as {@code BOSS_JIG} is (wiki/frontiermode/architecture/keeper.md § The Keeper
 * record). Registered as a {@code BOTH} factory from {@code KeeperModule.init()}, so each side builds its own
 * config and the registration supplies the side. Its tick runs the path reconciliation and, on the client, the
 * flash; the creation trigger is its handler for {@code BORDERS_JIG}'s load.
 */
public final class KeeperJigConfig {

    private KeeperJigConfig() {
    }

    public static LevelJigConfig build() {
        var keeperFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.KEEPER,
                        KeeperFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var keeperBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.KEEPER_BUNDLE,
                        (LevelScope scope) -> new KeeperBundle(scope, FrontierKeys.KEEPER_BUNDLE),
                        List.of(keeperFixture)
                );

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.KEEPER_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(keeperBundle)));
        // Persisted and networked: persistence is declared in three places, all required -- the capability
        // flag, the matching policy, and KeeperBundle.required(). Networking is declared in two, both required -- the
        // second capability flag and KeeperBundle.required().
        config.policies().capabilities(new JigPolicies.Capabilities(true, true, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));
        // withExecutionPulse(true): persistence flush and client sync only ever run from the execution pulse.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, KeeperJigHandlers::onBordersScopeLoaded)
                        .build());
        // The factory runs once per side, and a Trinket exists on both: each acts on its own side only.
        config.trinkets().declare(PathReconciliation.class, PathReconciliation::new);
        config.trinkets().declare(KeeperFlash.class, KeeperFlash::new);
        return config;
    }
}
