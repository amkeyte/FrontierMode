package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.sentinel.common.bundle.SentinelBundle;
import com.arryn.frontiermode.sentinel.common.fixture.SentinelFixture;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.LivingConversionSignal;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;

import java.util.List;

/**
 * Builds {@code SENTINEL_JIG} (wiki/frontiermode/architecture/sentinels.md § The Sentinel module): a
 * {@code LevelJigConfig} holding {@link SentinelFixture}, the synced record of the Sentinels present, in
 * {@link SentinelBundle}. Networked, not persisted: the fixture rebuilds from presence after a restart. Registered as a
 * {@code BOTH} factory from {@code SentinelModule.init()}, so each side builds its own config and the registration
 * supplies the side. The factory runs once per side and a Trinket exists on both, so {@link SentinelPresence} acts on
 * the server only and {@link SentinelClient} on the client only.
 */
final class SentinelJigConfig {

    private SentinelJigConfig() {
    }

    static LevelJigConfig build() {
        var sentinelFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.SENTINEL,
                        SentinelFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var sentinelBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.SENTINEL_BUNDLE,
                        (LevelScope scope) -> new SentinelBundle(scope, FrontierKeys.SENTINEL_BUNDLE),
                        List.of(sentinelFixture)
                );

        // Bare, unscoped signals -- declared here only because this is the topically closest config, not a claim
        // they are SENTINEL_JIG-scoped.
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(EntityJoinLevelSignal.class, SentinelJigHandlers::onEntityJoinLevel)
                        .on(SpawnPlacementCheckSignal.class, SentinelJigHandlers::onSpawnPlacementCheck)
                        .on(SpawnPositionCheckSignal.class, SentinelJigHandlers::onSpawnPositionCheck)
                        .on(LivingConversionSignal.class, SentinelJigHandlers::onConversion)
                        .build();

        // Networking requires BOTH (JigConfigValidator); the registration supplies it.
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.SENTINEL_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(sentinelBundle)));
        config.policies().capabilities(new JigPolicies.Capabilities(false, true, false));
        // The tick runs the Trinkets and the fixture's coalesced push flush; the execution pulse drives client sync.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);
        // The factory runs once per side, and a Trinket exists on both: each acts on its own side only.
        config.trinkets().declare(SentinelPresence.class, SentinelPresence::new);
        config.trinkets().declare(SentinelClient.class, SentinelClient::new);
        return config;
    }
}
