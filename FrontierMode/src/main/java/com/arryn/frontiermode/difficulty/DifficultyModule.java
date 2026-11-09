package com.arryn.frontiermode.difficulty;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;

/**
 * The stat ladder (difficulty.md): every hostile mob in a Border carries a health and a damage
 * multiplier, set once when it appears, kept on the mob in Satchel's {@code MobData}. This module owns
 * the formula, the Tunables, the hostile test and the three consumers; Boss calls it through
 * {@link DifficultyAPI} for Sentinels. It depends on Border for the ambient Difficulty lookup. The module
 * keeps no jig: it hears the game through {@link DifficultyIngress}.
 */
public final class DifficultyModule {

    private DifficultyModule() {
    }

    public static void init() {
        OUT.info("[Difficulty] init(): Module initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> DifficultyIngress.build());
    }
}
