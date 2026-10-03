package com.arryn.frontiermode.difficulty.server.rules;

import com.arryn.satchel.common.tunable.Tunable;

/**
 * Safe-baseline ladder numbers (difficulty.md § Module shape). Tunable-backed, constructed fresh at
 * each read; the Tunables live here, in the Rules class, by convention.
 */
public final class DefaultDifficultyRules implements DifficultyRules {

    @Override
    public double ordinaryHealthGrowth() {
        return new Tunable("ordinaryHealthGrowth", 0f, 1f, 0.075f).floatValue();
    }

    @Override
    public double ordinaryDamageGrowth() {
        return new Tunable("ordinaryDamageGrowth", 0f, 1f, 0.075f).floatValue();
    }

    @Override
    public double sentinelHealthGrowthFar() {
        return new Tunable("sentinelHealthGrowthFar", 0f, 1f, 0.01f).floatValue();
    }

    @Override
    public double sentinelHealthGrowthNear() {
        return new Tunable("sentinelHealthGrowthNear", 0f, 1f, 0.1f).floatValue();
    }

    @Override
    public double sentinelDamageGrowthFar() {
        return new Tunable("sentinelDamageGrowthFar", 0f, 1f, 0.01f).floatValue();
    }

    @Override
    public double sentinelDamageGrowthNear() {
        return new Tunable("sentinelDamageGrowthNear", 0f, 1f, 0.1f).floatValue();
    }
}
