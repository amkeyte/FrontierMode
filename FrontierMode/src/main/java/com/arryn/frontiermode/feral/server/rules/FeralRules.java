package com.arryn.frontiermode.feral.server.rules;

/**
 * Tunables for Feral (RM_FRO_038 "Martha") -- see {@code FeralLogic.intensity}. Placeholder
 * baselines, not playtest-tuned, same "safe baseline, replace later" convention every other
 * rules/constants class in this codebase follows (e.g. {@code PlayerRules},
 * {@code DefaultBossRules}).
 */
public final class FeralRules {

    private FeralRules() {}

    /**
     * Ferality intensity = frontierDistance * this -- uncapped. (Every hostile outside the
     * Frontier is Feral unconditionally, per the 2026-09-18 design ruling -- there is no longer a
     * conversion-chance constant here to bound; this only scales how twisted/dangerous a Feral
     * mob is once it exists.)
     */
    public static final double INTENSITY_SCALE = 0.05;

    /** Stat-scaling factor per unit of intensity -- same shape as Guardian's own health-scale constant. */
    public static final double HEALTH_SCALE = 1.0;

    // ------------------------------------------------------------------
    // Density -- extra hostile companions near a hostile that already spawned in the Exterior,
    // same "approximation of a true spawn-rate multiplier" shape Sick Wildlife's own density
    // boost uses (FrontierMode can't retroactively multiply spawn attempts, only piggyback on ones
    // that already happened). Design ruling (2026-09-18): scales with raw distance, uncapped --
    // see FeralLogic.expectedDensityCount, which replaced the earlier flat saturating multiplier.
    // ------------------------------------------------------------------

    /**
     * Expected extra companions = frontierDistance * this -- uncapped, same shape as
     * INTENSITY_SCALE. Design target (project owner, 2026-09-19): player movement should be
     * unmanageable within 100 blocks of the border, deliberately without touching the minimum
     * spawn radius -- density alone has to carry that. At 0.08, the expectation reaches
     * DENSITY_MAX_PER_EVENT (8) at exactly 100 blocks out, so "unmanageable by 100" is where the
     * ramp and the safety valve meet by construction, not a coincidence to re-tune separately.
     * Roughly 4x the original 0.02 baseline.
     */
    public static final double DENSITY_COUNT_SCALE = 0.08;

    /**
     * Safety valve, not a design cap: bounds how many companions one single spawn event can ever
     * create in one go, so an extreme distance can't turn one spawn into a lag spike. Tied to
     * DENSITY_COUNT_SCALE above -- this is also the intended full-density value at the 100-block
     * design target, not just an arbitrary ceiling. See this project's own "how many mobs before
     * lag" discussion, deferred rather than measured, per project owner's own call.
     */
    public static final int DENSITY_MAX_PER_EVENT = 8;

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
