package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.bundle.SentinelPublicBundle;
import com.arryn.frontiermode.boss.common.fixture.SentinelPublicFixture;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.ScopeEvent;

import java.util.List;

/**
 * Builds {@code SENTINEL_PUBLIC_JIG}: the client-visible sentinel record, in its own {@code BOTH} jig. Networked, not
 * persisted; the fixture's own tick flushes its coalesced push. Registered from {@code BossModule.init()} as a
 * {@code BOTH} factory, so each side builds its own config. Node G moves it with the Sentinel module.
 */
final class SentinelPublicJigConfig {

    private SentinelPublicJigConfig() {
    }

    static LevelJigConfig build() {
        var sentinelPublicFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.SENTINEL_PUBLIC,
                        SentinelPublicFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var sentinelPublicBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.SENTINEL_PUBLIC_BUNDLE,
                        (LevelScope scope) -> new SentinelPublicBundle(scope, FrontierKeys.SENTINEL_PUBLIC_BUNDLE),
                        List.of(sentinelPublicFixture)
                );

        // Networking requires BOTH (JigConfigValidator); the registration supplies it.
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.SENTINEL_PUBLIC_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(sentinelPublicBundle)));
        config.policies().capabilities(new JigPolicies.Capabilities(false, true, false));
        // The execution pulse drives client sync; the tick runs the fixture's coalesced push flush
        // and, client side, the trail.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, SentinelJigHandlers::onSentinelPublicTick)
                        .build());
        return config;
    }
}
