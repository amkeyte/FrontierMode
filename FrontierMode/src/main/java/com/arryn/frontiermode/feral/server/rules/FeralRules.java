package com.arryn.frontiermode.feral.server.rules;

import com.arryn.satchel.common.tunable.Tunable;
import com.arryn.satchel.common.util.out.OUT;

/**
 * Tunables for Feral (RM_FRO_038 "Martha") -- see {@code FeralLogic.intensity}. Placeholder
 * baselines, not playtest-tuned, same "safe baseline, replace later" convention every other
 * rules/constants class in this codebase follows (e.g. {@code PlayerRules},
 * {@code DefaultBossRules}).
 *
 * <p>FRO_141: twelve of these are now {@link Tunable}-backed (SAT_074) instead of plain constants
 * -- accessor <b>methods</b>, not fields, since a {@code Tunable} is constructed fresh at point of
 * use rather than cached (see that class's own doc). Every other constant in this file is still a
 * plain field, untouched -- FRO_141's own scope named exactly these twelve as the first real
 * consumer.
 */
public final class FeralRules {

    private FeralRules() {}

    /**
     * Ferality intensity = frontierDistance * this -- uncapped. (Every hostile outside the
     * Frontier is Feral unconditionally, per the 2026-09-18 design ruling -- there is no longer a
     * conversion-chance constant here to bound; this only scales how twisted/dangerous a Feral
     * mob is once it exists.)
     */
    public static float intensityScale() {
        return new Tunable("intensityScale", 0f, 1f, 0.05f).floatValue();
    }

    /** Stat-scaling factor per unit of intensity -- same shape as Guardian's own health-scale constant. */
    public static float healthScale() {
        return new Tunable("healthScale", 0f, 10f, 1.0f).floatValue();
    }

    // ------------------------------------------------------------------
    // Density -- extra hostile companions near a hostile that already spawned in the Wilds,
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
    public static float densityCountScale() {
        return new Tunable("densityCountScale", 0f, 1f, 0.08f).floatValue();
    }

    /**
     * Safety valve, not a design cap: bounds how many companions one single spawn event can ever
     * create in one go, so an extreme distance can't turn one spawn into a lag spike. Tied to
     * DENSITY_COUNT_SCALE above -- this is also the intended full-density value at the 100-block
     * design target, not just an arbitrary ceiling. See this project's own "how many mobs before
     * lag" discussion, deferred rather than measured, per project owner's own call.
     */
    public static int densityMaxPerEvent() {
        return new Tunable("densityMaxPerEvent", 0, 50, 8).intValue();
    }

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
    public static int tellIntervalTicks() {
        return new Tunable("tellIntervalTicks", 1, 200, 20).intValue();
    }

    /** Radius (blocks) around a player the cosmetic tell searches for Feral mobs. */
    public static float tellRadius() {
        return new Tunable("tellRadius", 0f, 128f, 16.0f).floatValue();
    }

    /** Independent per-mob roll chance (per tell interval) for the particle and the sound. */
    public static float tellParticleChance() {
        return new Tunable("tellParticleChance", 0f, 1f, 0.5f).floatValue();
    }

    public static float tellSoundChance() {
        return new Tunable("tellSoundChance", 0f, 1f, 0.15f).floatValue();
    }

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
    public static int reentrySweepIntervalTicks() {
        return new Tunable("reentrySweepIntervalTicks", 1, 200, 20).intValue();
    }

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
     * timer (see {@link #despawnLingerTicks()}), freeing its mob-cap slot once the timer fires.
     * "Safe baseline, replace later," like every other placeholder tunable in this class --
     * playtest feedback (2026-09-21) widened this from the original 10 to 20, the project
     * owner's own number, to keep a slightly wider close-in band alive as a visual cue.
     */
    public static float despawnRadius() {
        return new Tunable("despawnRadius", 0f, 200f, 20.0f).floatValue();
    }

    /**
     * How long (ticks) a Feral mob must stay continuously farther than {@link #despawnRadius()}
     * from every player before it's actually cleared -- a lingering window, not an instant cutoff,
     * so a mob doesn't flicker out and back as a player's distance oscillates right around the
     * threshold. Playtest feedback (2026-09-21, second pass): even 10s (200 ticks) was clearing
     * mobs fast enough to make the whole Wilds feel thin/underwhelming rather than a sustained
     * swarm -- project owner's own "like a minute" target, taken literally: 1200 ticks (60s).
     */
    public static int despawnLingerTicks() {
        return new Tunable("despawnLingerTicks", 0, 6000, 1200).intValue();
    }

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
     * mechanic budget (see {@link #maxAlivePackMembers()} for that). Originally enforced only
     * against {@code rollDensityCompanion}'s own extra-companion spawns (that mechanic is
     * disabled as of 2026-09-21, see {@code FeralModule.densityCompanionEnabled}) --
     * {@code FeralModule.maintainPackNearPlayer} respects it too now, for the same reason:
     * deliberately not the tagging decision itself ({@code onEntityJoinLevel}), which stays
     * unconditional per the 2026-09-18 design ruling ("every hostile outside the Frontier is
     * Feral"); only whichever mechanic is actively growing the population gets throttled once the
     * cap is reached. Well under vanilla's 70-mob hostile cap so real spawn room always remains --
     * project owner's own suggested number, not derived from anything besides "well under 70."
     */
    public static int maxAliveFeral() {
        return new Tunable("maxAliveFeral", 0, 70, 40).intValue();
    }

    /**
     * Dedicated ceiling on pack members specifically (see {@code FeralPackFixture}),
     * counted independently of ambient natural spawns via {@code FeralModule
     * .countAlivePackMembers}. Playtest feedback (2026-09-21, fifth pass): "we are getting a lot
     * of mobs from regular spawn algo as well, that's most of the cap" -- ambient Wilds
     * spawning was never itself capped, so it could (and did) consume the entire shared
     * {@link #maxAliveFeral()} budget and starve {@code maintainPackNearPlayer} of any room to
     * spawn at all. This reserves headroom ambient spawns can never crowd out. Twice
     * {@link #packTargetCount()} -- enough for one full pack near the player plus a trailing
     * previous batch still clearing out, not unbounded. Widened 24 -> 40 alongside
     * {@code packTargetCount()} (2026-09-22, "can we increase the pack number?") -- packMembers
     * was pinned at the old cap of 24 in the log that prompted this ask, so the cap itself was the
     * binding constraint, not just the target.
     */
    public static int maxAlivePackMembers() {
        return new Tunable("maxAlivePackMembers", 0, 200, 40).intValue();
    }

    // ------------------------------------------------------------------
    // Pack maintenance -- playtest feedback (2026-09-21): both the density-companion piggyback
    // and the distance despawn timer above left the Wilds feeling thin and easy to just weave
    // through for a couple hundred blocks before Frontier Sickness did the real work. Replaces
    // relying on vanilla's own sparse natural spawn attempts with a direct guarantee: while a
    // player is in the Wilds, keep a real pack of Feral mobs near *them* specifically. See
    // FeralModule.maintainPackNearPlayer/spawnPackMember.
    // ------------------------------------------------------------------

    /**
     * How many Feral mobs {@code FeralModule.maintainPackNearPlayer} tries to keep within
     * {@link #packMaintainRadius()} of a player who's in the Wilds. "Safe baseline, replace
     * later" -- picked to be a real, weave-through-it pack, not a token few; not playtest-tuned
     * against feel yet. Widened 12 -> 20 (2026-09-22, "can we increase the pack number?") --
     * {@link #maxAlivePackMembers()} widened alongside it, same 2x ratio as before.
     */
    public static int packTargetCount() {
        return new Tunable("packTargetCount", 0, 100, 20).intValue();
    }

    /**
     * Radius (blocks) {@code FeralModule.maintainPackNearPlayer} counts existing Feral mobs
     * within -- must stay comfortably larger than {@link #packSpawnMaxDistance()}, otherwise a
     * mob can spawn already outside this radius and never count toward its own quota.
     */
    public static float packMaintainRadius() {
        return new Tunable("packMaintainRadius", 8f, 256f, 64f).floatValue();
    }

    /**
     * Distance (blocks) beyond which an ordinary natural Wilds spawn (not a
     * pack member, {@code FeralPackFixture}) is discarded outright -- see
     * {@code FeralModule.despawnIfBeyondAmbientRadius}. Playtest feedback (2026-09-21, sixth
     * pass): "still hitting cap... remove all feral mobs spawned outside a radius, they are
     * inconsequential." Flat and direction-agnostic, unlike {@link #packDespawnRadius()}/
     * {@link #packBehindDespawnRadius()} -- an ambient spawn was never part of a deliberate
     * encounter, so it doesn't earn the pack mechanic's generous ahead-leash. Reuses
     * {@link #packMaintainRadius()}'s own value rather than picking a fresh number: if a natural
     * spawn isn't even within the zone the pack mechanic itself considers "near the player," it
     * was never going to be encountered either.
     */
    public static float ambientDespawnRadius() {
        // Deliberately tied to packMaintainRadius() rather than its own Tunable -- see the doc above.
        return packMaintainRadius();
    }

    /**
     * Distance (blocks) beyond which a Feral mob still *ahead* of the player is discarded
     * outright, no linger -- see {@code FeralModule.despawnIfBeyondPackRadius}. Deliberately well
     * past {@link #packMaintainRadius()}, not equal to it: the gap between the two is what keeps
     * a player's distance oscillating near either threshold from constantly spawning and
     * despawning the same mobs, without needing a per-mob timer to smooth it out. Only applies to
     * mobs ahead of the player -- see {@link #packBehindDespawnRadius()} for mobs the player has
     * already passed.
     */
    public static float packDespawnRadius() {
        return new Tunable("packDespawnRadius", 8f, 512f, 100f).floatValue();
    }

    /**
     * Distance (blocks) beyond which a Feral mob *behind* the player is discarded, regardless of
     * how far {@link #packDespawnRadius()} allows an ahead mob to stay. Playtest feedback
     * (2026-09-21, fifth pass): "hitting mob caps again" -- {@code
     * FeralModule.applyPackSpeedBoost}'s speed boost lets a passed mob keep pace well enough from
     * behind that it can trail the player indefinitely without ever covering
     * {@code packDespawnRadius()} worth of raw distance, quietly eating
     * {@link #maxAliveFeral()}'s budget forever. Small on purpose -- a short trailing beat after
     * being passed, not an instant vanish, but nowhere near the 100-block leash an ahead mob gets.
     * Widened 12 -> 20 per playtest feedback (2026-09-21, seventh pass).
     */
    public static float packBehindDespawnRadius() {
        return new Tunable("packBehindDespawnRadius", 0f, 256f, 20f).floatValue();
    }

    /** How often (ticks) {@code FeralModule.maintainPackNearPlayer} checks/tops up each player's pack. */
    // Halved 20 -> 10 (2026-09-21, eighth pass) per "maybe cadence needs to bump" -- the actual
    // stall-out bug was the hemisphere-vs-cone "ahead" test (see FeralModule.isAheadOfPlayer's
    // own doc), but a tighter check interval is a cheap, low-risk complement regardless.
    public static int packMaintainIntervalTicks() {
        return new Tunable("packMaintainIntervalTicks", 1, 200, 10).intValue();
    }

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
    public static float packSpawnConeHalfAngleDegrees() {
        return new Tunable("packSpawnConeHalfAngleDegrees", 1f, 180f, 40f).floatValue();
    }

    /** Nearest distance (blocks) a pack member can spawn from the player, within the cone above. */
    public static float packSpawnMinDistance() {
        return new Tunable("packSpawnMinDistance", 0f, 128f, 20f).floatValue();
    }

    /** Farthest distance (blocks) a pack member can spawn from the player, within the cone above. */
    public static float packSpawnMaxDistance() {
        return new Tunable("packSpawnMaxDistance", 1f, 256f, 45f).floatValue();
    }

    /**
     * {@code Attributes.FOLLOW_RANGE} applied to every spawned pack member -- playtest ask
     * (2026-09-22): wider aggro range. Vanilla's own zombie default is meaningfully shorter than
     * {@link #packSpawnMaxDistance()}, so a pack member landing at the far edge of the cone
     * could spawn already outside its own vanilla target-acquisition range and just stand there.
     * Comfortably past {@code packSpawnMaxDistance()} so every pack member notices the player
     * immediately, wherever in the cone it lands.
     */
    public static float packFollowRange() {
        return new Tunable("packFollowRange", 8f, 256f, 64f).floatValue();
    }

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
    public static int packSpeedAmplifier() {
        return new Tunable("packSpeedAmplifier", 0, 10, 5).intValue();
    }

    /**
     * Duration (ticks) of the speed boost above -- generous (30s) rather than tied to actual
     * engagement time, since there's no cheap signal for "this mob stopped being relevant."
     * {@link #packDespawnRadius()} is what actually ends a mob's relevance, not this running out.
     */
    public static int packSpeedDurationTicks() {
        return new Tunable("packSpeedDurationTicks", 20, 6000, 600).intValue();
    }

    // ------------------------------------------------------------------
    // Pack Tunable consistency check (FRO_141). The pack Tunables have relationships the doc
    // comments above state but nothing enforces -- warn instead of clamping (project owner call),
    // so a bad CSV edit is diagnosed from the log rather than silently "corrected" or silently
    // broken.
    // ------------------------------------------------------------------

    // The violations reported on the last check, so each is logged once when it appears (and once
    // when it clears), not every maintenance pass.
    private static java.util.Set<String> lastReportedPackProblems = java.util.Set.of();

    /**
     * Checks the pack Tunables' documented relationships and OUT.warns about any that are out of
     * whack. Edge-triggered: a given problem is logged when it first appears and again only if it
     * clears and comes back; a resolved set is noted once at INFO. Cheap enough to call from the
     * pack maintenance pass. Never changes a value.
     */
    public static void warnIfPackTunablesInconsistent() {
        float minDist = packSpawnMinDistance();
        float maxDist = packSpawnMaxDistance();
        float maintain = packMaintainRadius();
        float despawn = packDespawnRadius();

        java.util.Set<String> problems = new java.util.LinkedHashSet<>();
        if (minDist >= maxDist) {
            problems.add("packSpawnMinDistance (" + minDist + ") >= packSpawnMaxDistance (" + maxDist
                    + ") -- the pack spawn-distance roll goes zero/negative, so members spawn at or inside"
                    + " the minimum distance");
        }
        if (maintain <= maxDist) {
            problems.add("packMaintainRadius (" + maintain + ") <= packSpawnMaxDistance (" + maxDist
                    + ") -- a member can spawn already outside the counted radius and never counts toward"
                    + " its own quota, so the pack keeps over-spawning");
        }
        if (despawn <= maintain) {
            problems.add("packDespawnRadius (" + despawn + ") <= packMaintainRadius (" + maintain
                    + ") -- ahead pack members are despawned while still inside the counted radius, so the"
                    + " pack is destroyed and respawned in a loop");
        }
        int target = packTargetCount();
        int cap = maxAlivePackMembers();
        if (cap < target) {
            problems.add("maxAlivePackMembers (" + cap + ") < packTargetCount (" + target
                    + ") -- the pack can never reach its target size");
        }

        if (problems.equals(lastReportedPackProblems)) {
            return;
        }
        for (String problem : problems) {
            if (!lastReportedPackProblems.contains(problem)) {
                OUT.warn("[Feral] Pack Tunables out of whack: " + problem + ". (Not clamped -- fix the"
                        + " values in the tunables CSV.)");
            }
        }
        if (problems.isEmpty()) {
            OUT.info("[Feral] Pack Tunables are consistent again.");
        }
        lastReportedPackProblems = java.util.Set.copyOf(problems);
    }
}
