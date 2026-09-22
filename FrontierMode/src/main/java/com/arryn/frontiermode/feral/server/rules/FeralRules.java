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

    // ------------------------------------------------------------------
    // Distant despawn -- project owner report (2026-09-20): crossing a border spawns a real
    // swarm via the density mechanic, but it only ever fires once -- the initial burst saturates
    // vanilla's own local mob cap, so NaturalSpawner stops attempting new spawns near the player
    // at all, and rollDensityCompanion has nothing left to piggyback on (it can only react to a
    // real spawn attempt, never create one itself -- same limitation FeralRules' own density
    // section already documents). Meanwhile the mobs from that first burst keep wandering off
    // and just sit there occupying cap slots indefinitely, doing nothing "the basic idea is to
    // swarm the player" actually needs.
    //
    // FRO_128 (project owner refinement, 2026-09-21): the original instant-discard-at-50-blocks
    // fix cleared distant mobs, but also left a large, useless pile of them sitting right at the
    // border doing nothing but eating the vanilla hostile cap (70 total) -- a swarm that reads as
    // pure eye candy rather than a threat, and crowds out room for real spawns once the player
    // actually crosses. Rationale (project owner): keep a tight, close-in band of visible Feral
    // mobs right at the border as a visual cue while the player's still deciding whether to
    // cross, but clear anything that's drifted out of that immediate vicinity quickly, so the
    // density mechanic has cap room to build the real "zombie apocalypse" swarm once the player
    // is genuinely out in the wild rather than staying capped-out on stale eye candy. A shorter
    // radius (10, not 50) plus a lingering timer (not instant) is the shape that serves both --
    // instant-at-10 would make mobs flicker out from under a player standing right at the border;
    // a short linger tolerates that without letting mobs pile up far away for long. Replaces the
    // 50-block instant-discard approach entirely, not a second check alongside it -- see
    // FeralModule.sweepFeralCleanup.
    // ------------------------------------------------------------------

    /**
     * Distance (blocks) from the nearest player beyond which a Feral mob starts its despawn
     * timer (see {@link #DESPAWN_LINGER_TICKS}), freeing its mob-cap slot once the timer fires.
     * "Safe baseline, replace later," like every other placeholder tunable in this class -- the
     * project owner's own suggested number, chosen to keep only an immediate, close-in band of
     * mobs alive as a visual cue rather than a wide static pile.
     */
    public static final double DESPAWN_RADIUS = 10.0;

    /**
     * How long (ticks) a Feral mob must stay continuously farther than {@link #DESPAWN_RADIUS}
     * from every player before it's actually cleared -- a lingering window, not an instant cutoff,
     * so a mob doesn't flicker out and back as a player's distance oscillates right around the
     * threshold. Placeholder baseline (5s), not playtest-tuned.
     */
    public static final int DESPAWN_LINGER_TICKS = 100;

    // ------------------------------------------------------------------
    // Total population cap -- project owner rationale (2026-09-21): vanilla's own hostile mob
    // cap is 70 per player/dimension, shared by every hostile in the level, not just Feral's own.
    // Standing right at the border (not yet crossing) can build a static "wall" of Feral mobs via
    // the density mechanic that DESPAWN_RADIUS/DESPAWN_LINGER_TICKS above won't touch -- they're
    // well within DESPAWN_RADIUS of the player the whole time, so nothing ever times them out.
    // Left unbounded, that wall can grow to consume most of the 70-cap on mobs that are pure eye
    // candy (the visual "there's danger out there" cue), crowding out both ordinary in-border
    // spawns and the density mechanic's own ability to keep piggybacking on new spawn attempts
    // once the player actually crosses and needs the real "zombie apocalypse" swarm.
    // ------------------------------------------------------------------

    /**
     * Ceiling on how many Feral-tagged mobs may be alive across a level at once, enforced only
     * against {@code rollDensityCompanion}'s own extra-companion spawns -- deliberately not the
     * tagging decision itself ({@code onEntityJoinLevel}), which stays unconditional per the
     * 2026-09-18 design ruling ("every hostile outside the Frontier is Feral"). A natural spawn
     * always gets tagged regardless of this cap; only the density mechanic's own contribution to
     * total count is throttled once the cap is reached, since that's the actual growth driver
     * behind the border-wall problem this constant exists for. Well under vanilla's 70-mob
     * hostile cap so real spawn room always remains -- project owner's own suggested number, not
     * derived from anything besides "well under 70."
     */
    public static final int MAX_ALIVE_FERAL = 40;
}
