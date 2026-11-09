package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.structure.common.CairnFixture;
import com.arryn.frontiermode.structure.common.StructureBundle;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.List;

/**
 * Builds {@code STRUCTURE_JIG}: a {@code SERVER}-only {@code LevelJigConfig} holding {@link CairnFixture} in
 * {@link StructureBundle} (wiki/frontiermode/architecture/cairn.md#data-model). Persisted, not networked; it
 * ticks, with the execution pulse on, and {@link CairnPlacementTick} is its Trinket. Registered from
 * {@code StructureModule.init()} as a {@code SERVER} factory.
 */
public final class StructureJigConfig {

    private StructureJigConfig() {
    }

    public static LevelJigConfig build() {
        var cairnFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.CAIRN,
                        CairnFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var structureBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.STRUCTURE_BUNDLE,
                        (LevelScope scope) -> new StructureBundle(scope, FrontierKeys.STRUCTURE_BUNDLE),
                        List.of(cairnFixture)
                );

        JigBundles.Schema<LevelScope> schema =
                new JigBundles.Schema<>(List.of(structureBundle));

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.STRUCTURE_JIG);
        config.bundles().schema(schema);
        // CairnFixture is real, persisted, world-anchored state -- both calls are needed explicitly
        // (see boss.md's "Module wiring").
        config.policies().capabilities(new JigPolicies.Capabilities(true, false, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true));
        config.trinkets().declare(CairnPlacementTick.class, CairnPlacementTick::new);

        return config;
    }
}
