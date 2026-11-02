package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.bosstell.common.bundle.TellBundle;
import com.arryn.frontiermode.bosstell.common.fixture.BossTellFixture;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.List;

/**
 * Builds {@code TELL_JIG} (wiki/frontiermode/architecture/discovery-systems.md § Environmental Tells): a
 * {@code SERVER}-only {@code LevelJigConfig} holding {@link BossTellFixture} in {@link TellBundle}. The client does
 * nothing with tells (they are broadcast sound and particles), so there is no networking. It ticks, with the execution
 * pulse on, and the fixture's tick is the tell pass.
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

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.TELL_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(tellBundle)));
        config.policies().capabilities(JigPolicies.Capabilities.defaults());
        config.policies().persistence(JigPolicies.Persistence.defaults());
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true));
        return config;
    }
}
