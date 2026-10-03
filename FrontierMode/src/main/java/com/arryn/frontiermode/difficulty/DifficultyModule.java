package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.lifecycle.AnimalTameSignal;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.LivingHurtSignal;
import com.arryn.satchel.common.util.out.OUT;

import java.util.List;

/**
 * The stat ladder (difficulty.md): every hostile mob in a Border carries a health and a damage
 * multiplier, set once when it appears, kept on the mob in Satchel's {@code MobData}. This module owns
 * the formula, the Tunables, the hostile test and the two handlers; Boss calls it through
 * {@link DifficultyAPI} for Sentinels. It depends on Border for the ambient Difficulty lookup.
 */
public final class DifficultyModule {

    private DifficultyModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Difficulty Module Initiating.");

        // Bare, unscoped signals -- installed on this config only because it is where installation
        // happens to occur, not a claim they are scoped to it.
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(EntityJoinLevelSignal.class, DifficultyHandlers::onEntityJoinLevel)
                        .on(LivingHurtSignal.class, DifficultyHandlers::onLivingHurt)
                        .on(AnimalTameSignal.class, DifficultyHandlers::onAnimalTame)
                        .build();

        // Exists purely to get a signal subscription point: no bundle or fixture, and no tick
        // (overrides LevelJigConfig's own withTick(true) default back to false).
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.DIFFICULTY_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults())
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }
}
