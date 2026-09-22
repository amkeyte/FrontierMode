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

    /**
     * Radius (blocks, both x and z, uniform) a density companion can land from the original
     * spawn's own position. Playtest feedback (2026-09-21): the original ±3 packed every
     * companion into a tight 7x7 clump right on top of the original spawn -- easy to dodge as one
     * blob instead of reading as a spread-out swarm. Widened by 8x to ±24 (a 49x49 area) so a
     * dense event actually forces the player to maneuver among mobs, not sidestep one pile.
     * "Safe baseline, replace later" -- not derived from anything besides "a lot bigger than 3."
     */
    public static final int DENSITY_COMPANION_SPREAD_RADIUS = 24;

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

    /**
     * How often (ticks) each level is swept for feral-team mobs that re-entered the Frontier, and
     * (since {@code FeralModule.despawnIfBeyondAmbientRadius}) for ambient/pack cleanup too.
     * Halved 40 -> 20 (2026-09-22, playtest diagnostics): a sprinting player loading fresh chunks
     * continuously generates ambient Feral spawns fast enough that a 2s sweep interval let the
     * ambient count balloon well past {@code MAX_ALIVE_FERAL} between prunes -- see FRO_144's log
     * for the full trace. Not the sole fix (see {@code FeralModule.maintainPackNearPlayer}'s own
     * doc for the actual budget bug), but a tighter interval keeps that overshoot smaller.
     */
    public static final int REENTRY_SWEEP_INTERVAL_TICKS = 20;

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
     * "Safe baseline, replace later," like every other placeholder tunable in this class --
     * playtest feedback (2026-09-21) widened this from the original 10 to 20, the project
     * owner's own number, to keep a slightly wider close-in band alive as a visual cue.
     */
    public static final double DESPAWN_RADIUS = 20.0;

    /**
     * How long (ticks) a Feral mob must stay continuously farther than {@link #DESPAWN_RADIUS}
     * from every player before it's actually cleared -- a lingering window, not an instant cutoff,
     * so a mob doesn't flicker out and back as a player's distance oscillates right around the
     * threshold. Playtest feedback (2026-09-21, second pass): even 10s (200 ticks) was clearing
     * mobs fast enough to make the whole Exterior feel thin/underwhelming rather than a sustained
     * swarm -- project owner's own "like a minute" target, taken literally: 1200 ticks (60s).
     */
    public static final int DESPAWN_LINGER_TICKS = 1200;

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
     * Ceiling on how many Feral-tagged mobs (ambient natural spawns and pack members combined)
     * may be alive across a level at once -- a combined-population safety valve, not a per-
     * mechanic budget (see {@link #MAX_ALIVE_PACK_MEMBERS} for that). Originally enforced only
     * against {@code rollDensityCompanion}'s own extra-companion spawns (that mechanic is
     * disabled as of 2026-09-21, see {@code FeralModule.densityCompanionEnabled}) --
     * {@code FeralModule.maintainPackNearPlayer} respects it too now, for the same reason:
     * deliberately not the tagging decision itself ({@code onEntityJoinLevel}), which stays
     * unconditional per the 2026-09-18 design ruling ("every hostile outside the Frontier is
     * Feral"); only whichever mechanic is actively growing the population gets throttled once the
     * cap is reached. Well under vanilla's 70-mob hostile cap so real spawn room always remains --
     * project owner's own suggested number, not derived from anything besides "well under 70."
     */
    public static final int MAX_ALIVE_FERAL = 40;

    /**
     * Dedicated ceiling on pack members specifically (see {@code FeralModule.PACK_MEMBER_TAG}),
     * counted independently of ambient natural spawns via {@code FeralModule
     * .countAlivePackMembers}. Playtest feedback (2026-09-21, fifth pass): "we are getting a lot
     * of mobs from regular spawn algo as well, that's most of the cap" -- ambient Exterior
     * spawning was never itself capped, so it could (and did) consume the entire shared
     * {@link #MAX_ALIVE_FERAL} budget and starve {@code maintainPackNearPlayer} of any room to
     * spawn at all. This reserves headroom ambient spawns can never crowd out. Twice
     * {@link #PACK_TARGET_COUNT} -- enough for one full pack near the player plus a trailing
     * previous batch still clearing out, not unbounded. Widened 24 -> 40 alongside
     * {@code PACK_TARGET_COUNT} (2026-09-22, "can we increase the pack number?") -- packMembers
     * was pinned at the old cap of 24 in the log that prompted this ask, so the cap itself was the
     * binding constraint, not just the target.
     */
    public static final int MAX_ALIVE_PACK_MEMBERS = 40;

    // ------------------------------------------------------------------
    // Pack maintenance -- playtest feedback (2026-09-21): both the density-companion piggyback
    // and the distance despawn timer above left the Exterior feeling thin and easy to just weave
    // through for a couple hundred blocks before Frontier Sickness did the real work. Replaces
    // relying on vanilla's own sparse natural spawn attempts with a direct guarantee: while a
    // player is in the Exterior, keep a real pack of Feral mobs near *them* specifically. See
    // FeralModule.maintainPackNearPlayer/spawnPackMember.
    // ------------------------------------------------------------------

    /**
     * How many Feral mobs {@code FeralModule.maintainPackNearPlayer} tries to keep within
     * {@link #PACK_MAINTAIN_RADIUS} of a player who's in the Exterior. "Safe baseline, replace
     * later" -- picked to be a real, weave-through-it pack, not a token few; not playtest-tuned
     * against feel yet. Widened 12 -> 20 (2026-09-22, "can we increase the pack number?") --
     * {@link #MAX_ALIVE_PACK_MEMBERS} widened alongside it, same 2x ratio as before.
     */
    public static final int PACK_TARGET_COUNT = 20;

    /**
     * Radius (blocks) {@code FeralModule.maintainPackNearPlayer} counts existing Feral mobs
     * within -- must stay comfortably larger than {@link #PACK_SPAWN_MAX_DISTANCE}, otherwise a
     * mob can spawn already outside this radius and never count toward its own quota.
     */
    public static final double PACK_MAINTAIN_RADIUS = 64.0;

    /**
     * Distance (blocks) beyond which an ordinary natural Exterior spawn (not a
     * {@code FeralModule.isPackMember}-marked one) is discarded outright -- see
     * {@code FeralModule.despawnIfBeyondAmbientRadius}. Playtest feedback (2026-09-21, sixth
     * pass): "still hitting cap... remove all feral mobs spawned outside a radius, they are
     * inconsequential." Flat and direction-agnostic, unlike {@link #PACK_DESPAWN_RADIUS}/
     * {@link #PACK_BEHIND_DESPAWN_RADIUS} -- an ambient spawn was never part of a deliberate
     * encounter, so it doesn't earn the pack mechanic's generous ahead-leash. Reuses
     * {@link #PACK_MAINTAIN_RADIUS}'s own value rather than picking a fresh number: if a natural
     * spawn isn't even within the zone the pack mechanic itself considers "near the player," it
     * was never going to be encountered either.
     */
    public static final double AMBIENT_DESPAWN_RADIUS = PACK_MAINTAIN_RADIUS;

    /**
     * Distance (blocks) beyond which a Feral mob still *ahead* of the player is discarded
     * outright, no linger -- see {@code FeralModule.despawnIfBeyondPackRadius}. Deliberately well
     * past {@link #PACK_MAINTAIN_RADIUS}, not equal to it: the gap between the two is what keeps
     * a player's distance oscillating near either threshold from constantly spawning and
     * despawning the same mobs, without needing a per-mob timer to smooth it out. Only applies to
     * mobs ahead of the player -- see {@link #PACK_BEHIND_DESPAWN_RADIUS} for mobs the player has
     * already passed.
     */
    public static final double PACK_DESPAWN_RADIUS = 100.0;

    /**
     * Distance (blocks) beyond which a Feral mob *behind* the player is discarded, regardless of
     * how far {@link #PACK_DESPAWN_RADIUS} allows an ahead mob to stay. Playtest feedback
     * (2026-09-21, fifth pass): "hitting mob caps again" -- {@code
     * FeralModule.applyPackSpeedBoost}'s speed boost lets a passed mob keep pace well enough from
     * behind that it can trail the player indefinitely without ever covering
     * {@code PACK_DESPAWN_RADIUS} worth of raw distance, quietly eating
     * {@link #MAX_ALIVE_FERAL}'s budget forever. Small on purpose -- a short trailing beat after
     * being passed, not an instant vanish, but nowhere near the 100-block leash an ahead mob gets.
     * Widened 12 -> 20 per playtest feedback (2026-09-21, seventh pass).
     */
    public static final double PACK_BEHIND_DESPAWN_RADIUS = 20.0;

    /** How often (ticks) {@code FeralModule.maintainPackNearPlayer} checks/tops up each player's pack. */
    // Halved 20 -> 10 (2026-09-21, eighth pass) per "maybe cadence needs to bump" -- the actual
    // stall-out bug was the hemisphere-vs-cone "ahead" test (see FeralModule.isAheadOfPlayer's
    // own doc), but a tighter check interval is a cheap, low-risk complement regardless.
    public static final int PACK_MAINTAIN_INTERVAL_TICKS = 10;

    // ------------------------------------------------------------------
    // Spawn placement/speed -- playtest feedback (2026-09-21, third pass): a plain radius around
    // the player's own current position spawned the pack in whatever direction they'd already
    // left behind, since a sprinting player outpaces both the 1s maintenance cadence and a
    // zombie's own walk speed -- "I'm just moving faster than the spawning can keep up." Fourth
    // pass ("got one pack... but I still passed them," "put them ahead more and spread them way
    // out"): widened both further, and paired with FeralModule.isAheadOfPlayer so a pack the
    // player has already run past stops blocking a fresh one from spawning ahead of them. See
    // FeralModule.spawnPackMember/maintainPackNearPlayer's own docs for the full reasoning.
    // ------------------------------------------------------------------

    /**
     * Half-angle (degrees) of the cone pack members spawn within, either side of the player's own
     * yaw -- replaces the original single-point-plus-square-scatter placement (2026-09-21, eighth
     * pass: "are we doing a wedge or more like a cone to the front?"). Also the same cone
     * {@code FeralModule.isAheadOfPlayer} tests membership against, not a plain dot product --
     * see that method's own doc for why a full 180-degree hemisphere test was letting stale,
     * off-angle mobs keep blocking fresh spawns. Force-spawned via a real {@code
     * ForgeEventFactory.onFinalizeSpawn} dispatch, so unlike a natural spawn this isn't bound by
     * vanilla's own minimum spawn-distance-from-player rule.
     */
    public static final double PACK_SPAWN_CONE_HALF_ANGLE_DEGREES = 40.0;

    /** Nearest distance (blocks) a pack member can spawn from the player, within the cone above. */
    public static final double PACK_SPAWN_MIN_DISTANCE = 20.0;

    /** Farthest distance (blocks) a pack member can spawn from the player, within the cone above. */
    public static final double PACK_SPAWN_MAX_DISTANCE = 45.0;

    /**
     * {@code Attributes.FOLLOW_RANGE} applied to every spawned pack member -- playtest ask
     * (2026-09-22): wider aggro range. Vanilla's own zombie default is meaningfully shorter than
     * {@link #PACK_SPAWN_MAX_DISTANCE}, so a pack member landing at the far edge of the cone
     * could spawn already outside its own vanilla target-acquisition range and just stand there.
     * Comfortably past {@code PACK_SPAWN_MAX_DISTANCE} so every pack member notices the player
     * immediately, wherever in the cone it lands.
     */
    public static final double PACK_FOLLOW_RANGE = 64.0;

    /**
     * {@code MobEffects.MOVEMENT_SPEED} amplifier applied to every spawned pack member -- a plain
     * zombie's walk speed can't sustain a chase against a sprinting player at all, so even a
     * perfectly forward-placed spawn falls behind within a few seconds without this. Amplifier is
     * Minecraft's own 0-indexed convention.
     *
     * <p>Playtest data (2026-09-22): at Speed IV (amplifier 3), a captured run's own
     * {@code nearestPack} diagnostic held a flat 1-5 blocks for a full 450-block chase before the
     * pack finally landed the kill -- a stable near-equilibrium, close enough to read as
     * dangerous but not actually closing the gap to contact for most of the run. "The chase was
     * definitely more exciting, let's get it down to about 100 blocks" -- bumped to Speed VI
     * (amplifier 5) so the pack can actually close that last stretch and connect sooner, instead
     * of trailing at a stable distance for hundreds of blocks. Not playtest-confirmed against
     * this specific target yet.
     */
    public static final int PACK_SPEED_AMPLIFIER = 5;

    /**
     * Duration (ticks) of the speed boost above -- generous (30s) rather than tied to actual
     * engagement time, since there's no cheap signal for "this mob stopped being relevant."
     * {@link #PACK_DESPAWN_RADIUS} is what actually ends a mob's relevance, not this running out.
     */
    public static final int PACK_SPEED_DURATION_TICKS = 600;
}
