package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.client.render.level.RenderContext;
import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.BorderPregenFixture;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.server.player.SicknessSwitch;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.List;

/**
 * Builds {@code BORDERS_JIG}: a {@code LevelJigConfig} holding the three fixtures of {@link BordersBundle},
 * persisted and networked (wiki/frontiermode/architecture/border.md § Runtime wiring). Registered as a
 * {@code BOTH} factory from {@code BorderModule.init()}, so each side builds its own config and the
 * registration supplies the side. {@code BorderModule.init()} runs first of the Border, Boss and Keeper
 * modules, because the Keeper's creation trigger is a handler for this jig's {@code Loaded} event.
 */
final class BorderJig {

    private BorderJig() {
    }

    static LevelJigConfig build() {
        var bordersFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BORDERS,
                        BordersFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        // Static descriptors, read on demand: no tick wiring of its own (border-curve.md, Data model).
        var curveFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.CURVE,
                        BorderCurveFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        // Its own onJigTick() drives its throttled work once this jig ticks.
        var pregenFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.PREGEN,
                        BorderPregenFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var bordersBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.BORDERS_BUNDLE,
                        (LevelScope scope) -> new BordersBundle(scope, FrontierKeys.BORDERS_BUNDLE),
                        List.of(bordersFixture, curveFixture, pregenFixture)
                );

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.BORDERS_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(bordersBundle)));

        // Persisted and networked. Persistence is declared in three places, all required: the capability flag,
        // the matching persistence policy (the config validator rejects a capability with no persistent policy
        // at foundation boot), and BordersBundle.required(). Networking is declared in two, both required: the
        // second capability flag and BordersBundle.required(). Without it the client's bundle never receives a
        // parcel, and the ring never draws.
        config.policies().capabilities(new JigPolicies.Capabilities(true, true, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));

        // withTick(true): BorderPregenFixture's onJigTick rides the jig's tick. withExecutionPulse(true):
        // the persistence flush and the client sync only ever run from the execution pulse.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true));
        // The factory runs once per side, and a Trinket exists on both, each acting on its own side only:
        // RenderContext on the client and SicknessSwitch on the server. Neither names a type that exists on
        // one side only. This jig carries no event handlers.
        config.trinkets().declare(RenderContext.class, RenderContext::new);
        config.trinkets().declare(SicknessSwitch.class, SicknessSwitch::new);
        return config;
    }
}
