package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.bosstell.common.bundle.TellBundle;
import com.arryn.frontiermode.bosstell.common.fixture.BossTellFixture;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.ScopeEvent;

import java.util.List;

/**
 * Builds {@code TELL_JIG} (wiki/frontiermode/architecture/discovery-systems.md § Environmental Tells): a
 * {@code SERVER}-only {@code LevelJigConfig} holding {@link BossTellFixture} in {@link TellBundle}. The client does
 * nothing with tells (they are broadcast sound and particles), so there is no networking. It ticks, with the execution
 * pulse on, and the fixture's tick is the tell pass. The bundle is created when the jig's scope loads, because a bundle
 * exists only once something creates it and nothing else asks for this one.
 *
 * <p>Not persisted, which departs from the page: the fixture keeps nothing durable. Its cadence counter, RNG and
 * per-boss sound rotation are transient, and the Border's {@code "tell"} curve is Border's own record, so a persisted
 * bundle would write an empty file per level. Registered from {@code BossTellModule.init()} as a {@code SERVER}
 * factory.
 */
final class TellJigConfig {

    private TellJigConfig() {
    }

    static LevelJigConfig build() {
        var tellFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BOSS_TELL,
                        BossTellFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var tellBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.TELL_BUNDLE,
                        (LevelScope scope) -> new TellBundle(scope, FrontierKeys.TELL_BUNDLE),
                        List.of(tellFixture)
                );

        // The per-side bus fires Loaded for every jig's scope, so the handler filters by jig.
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, TellJigConfig::onScopeLoaded)
                        .build();

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.TELL_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(tellBundle)));
        config.policies().capabilities(JigPolicies.Capabilities.defaults());
        config.policies().persistence(JigPolicies.Persistence.defaults());
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);
        return config;
    }

    /**
     * Creates the Tell bundle when {@code TELL_JIG}'s own scope loads (a module's own {@code Loaded} handler creates
     * its bundles, as {@code ScopeInfo.getOrCreate} documents). Without it the bundle never exists, and the server
     * engine ticks only bundles that exist, so the tell fixture would never run.
     */
    private static void onScopeLoaded(ScopeEvent.Loaded event) {
        if (event.isJig(FrontierKeys.TELL_JIG)) {
            event.info().getOrCreate(FrontierKeys.TELL_BUNDLE);
        }
    }
}
