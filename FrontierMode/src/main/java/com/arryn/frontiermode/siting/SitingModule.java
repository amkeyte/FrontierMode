package com.arryn.frontiermode.siting;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.lifecycle.CreateWorldSpawnSignal;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.util.out.OUT;

import java.util.List;

/**
 * Finding a spot that's good for a thing (siting.md): the shared terrain-suitability check, the
 * candidate generators, and the new-world starting-site search. Depends on Border; Boss, structure and
 * nothing else depend on it.
 */
public final class SitingModule {

    private SitingModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Siting Module Initiating.");

        // Bare, unscoped signal -- installed on this config only because it is where installation
        // happens to occur, not a claim it is scoped to it.
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(CreateWorldSpawnSignal.class, StartingSiteHandlers::onCreateWorldSpawn)
                        .build();

        // Exists purely to get a signal subscription point: no bundle or fixture, and no tick
        // (overrides LevelJigConfig's own withTick(true) default back to false).
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.SITING_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults())
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }
}
