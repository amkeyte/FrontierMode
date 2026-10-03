package com.arryn.frontiermode.difficulty.server.rules;

/**
 * The stat ladder's tuning (difficulty.md § Module shape). Health and damage use the same
 * {@code 1 + growth x (n - 1)} shape with separate growth factors, so they can be tuned apart.
 */
public interface DifficultyRules {

    DifficultyRules ACTIVE = new DefaultDifficultyRules();

    /** Growth per Difficulty for ordinary hostiles; every other rung adds to it. */
    double ordinaryHealthGrowth();

    double ordinaryDamageGrowth();

    /** A Sentinel's extra growth at the edge of the Sentinel zone (difficulty intensity 0). */
    double sentinelHealthGrowthFar();

    /** A Sentinel's extra growth beside the boss (difficulty intensity 1). */
    double sentinelHealthGrowthNear();

    double sentinelDamageGrowthFar();

    double sentinelDamageGrowthNear();

    /** A Keeper's extra growth per path position, on top of the ordinary growth. */
    double keeperHealthGrowth();

    double keeperDamageGrowth();

    /** Damage per hit of a vex an Evoker Keeper summons (vanilla health and size; boss.md § The Evoker). */
    double keeperVexDamage();

    /** The damage one ordinary vex hit deals on Normal, the reference {@link #keeperVexDamage} is scaled from. */
    double vexReferenceHit();
}
