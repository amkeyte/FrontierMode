package com.arryn.frontiermode.difficulty.common;

import com.arryn.satchel.common.tunable.Tunable;

/**
 * The stat ladder's tuning (difficulty.md § Module shape): safe-baseline numbers, Tunable-backed and constructed
 * fresh at each read; the Tunables live here, in the Rules class, by convention. Health and damage use the same
 * {@code 1 + growth x (n - 1)} shape with separate growth factors, so they can be tuned apart.
 */
public final class DifficultyRules {

    private DifficultyRules() {
    }

    /** Growth per Difficulty for ordinary hostiles; every other rung adds to it. */
    public static double ordinaryHealthGrowth() {
        return new Tunable("ordinaryHealthGrowth", 0f, 1f, 0.075f).floatValue();
    }

    public static double ordinaryDamageGrowth() {
        return new Tunable("ordinaryDamageGrowth", 0f, 1f, 0.075f).floatValue();
    }

    /** A Sentinel's extra growth at the edge of the Sentinel zone (difficulty intensity 0). */
    public static double sentinelHealthGrowthFar() {
        return new Tunable("sentinelHealthGrowthFar", 0f, 1f, 0.01f).floatValue();
    }

    /** A Sentinel's extra growth beside the boss (difficulty intensity 1). */
    public static double sentinelHealthGrowthNear() {
        return new Tunable("sentinelHealthGrowthNear", 0f, 1f, 0.1f).floatValue();
    }

    public static double sentinelDamageGrowthFar() {
        return new Tunable("sentinelDamageGrowthFar", 0f, 1f, 0.01f).floatValue();
    }

    public static double sentinelDamageGrowthNear() {
        return new Tunable("sentinelDamageGrowthNear", 0f, 1f, 0.1f).floatValue();
    }

    /** A Feral mob's growth per path position (the feral rung; difficulty.md § The stat ladder). */
    public static double feralHealthGrowth() {
        return new Tunable("feralHealthGrowth", 0f, 1f, 0.075f).floatValue();
    }

    public static double feralDamageGrowth() {
        return new Tunable("feralDamageGrowth", 0f, 1f, 0.075f).floatValue();
    }

    /** A Keeper's extra growth per path position, on top of the ordinary growth. */
    public static double keeperHealthGrowth() {
        return new Tunable("keeperHealthGrowth", 0f, 2f, 0.31f).floatValue();
    }

    public static double keeperDamageGrowth() {
        return new Tunable("keeperDamageGrowth", 0f, 2f, 0.31f).floatValue();
    }

    /** Damage per hit of a vex an Evoker Keeper summons (vanilla health and size; boss.md § The Evoker). */
    public static double keeperVexDamage() {
        return new Tunable("keeperVexDamage", 0f, 100f, 12f).floatValue();
    }

    /**
     * The damage one ordinary vex hit deals on Normal, the reference {@link DifficultyRules#keeperVexDamage} is
     * scaled from.
     */
    public static double vexReferenceHit() {
        // A vex's sword hit on Normal.
        return 9.0;
    }
}
