package com.arryn.frontiermode.feral.server.rules;

/**
 * Tunables for Feral (RM_FRO_038 "Martha") -- see {@code FeralLogic.spawnChance}/{@code intensity}.
 * Placeholder baselines, not playtest-tuned, same "safe baseline, replace later" convention every
 * other rules/constants class in this codebase follows (e.g. {@code PlayerRules},
 * {@code DefaultBossRules}).
 */
public final class FeralRules {

    private FeralRules() {}

    /** Distance (blocks) at which Feral's own spawn chance is already ~63% of the way to its max. */
    public static final double SPAWN_FALLOFF_DISTANCE = 20.0;

    /** Spawn-roll chance Feral's own chance curve saturates toward far past the Frontier edge. */
    public static final double SPAWN_MAX_CHANCE = 0.5;

    /** Ferality intensity = frontierDistance * this -- uncapped, unlike the spawn chance above. */
    public static final double INTENSITY_SCALE = 0.05;

    /** Stat-scaling factor per unit of intensity -- same shape as Guardian's own health-scale constant. */
    public static final double HEALTH_SCALE = 1.0;

    // ------------------------------------------------------------------
    // Density boost -- an extra hostile companion near a hostile that already spawned in the
    // Exterior, same "approximation of a true spawn-rate multiplier" shape Sick Wildlife's own
    // density boost uses (FrontierMode can't retroactively multiply spawn attempts, only piggyback
    // on ones that already happened). Independent of the Feral-conversion roll above -- this fires
    // for any hostile in the Exterior, not just ones that became Feral.
    // ------------------------------------------------------------------

    /** Distance (blocks) at which the density boost is already ~63% of the way to its max. */
    public static final double DENSITY_FALLOFF_DISTANCE = 15.0;

    /** Flat multiplier the density boost saturates toward far past the Frontier edge. */
    public static final double DENSITY_MAX_MULTIPLIER = 2.0;

    // ------------------------------------------------------------------
    // Cosmetic tell -- poison particles / hurt sounds on already-spawned Feral mobs, mirroring
    // Sick Wildlife's own tell (PlayerRules.WILDLIFE_TELL_*) exactly. Purely cosmetic: doesn't
    // touch health/combat, just visibly marks a Feral mob as afflicted the same way sick wildlife
    // already is.
    // ------------------------------------------------------------------

    /** How often (ticks) the cosmetic tell rolls per player -- mirrors WILDLIFE_TELL_INTERVAL_TICKS. */
    public static final int TELL_INTERVAL_TICKS = 20;

    /** Radius (blocks) around a player the cosmetic tell searches for Feral mobs. */
    public static final double TELL_RADIUS = 16.0;

    /** Independent per-mob roll chance (per tell interval) for the particle and the sound. */
    public static final double TELL_PARTICLE_CHANCE = 0.5;
    public static final double TELL_SOUND_CHANCE = 0.15;

    // ------------------------------------------------------------------
    // Re-entry kill (FRO_111) -- a Feral mob that wanders back inside the Frontier must not
    // survive. Level-wide, not player-radius-bound like the cosmetic tell above -- the guarantee
    // has to hold everywhere a Feral mob might wander back to.
    // ------------------------------------------------------------------

    /** How often (ticks) each level is swept for feral-team mobs that re-entered the Frontier. */
    public static final int REENTRY_SWEEP_INTERVAL_TICKS = 40;
}
