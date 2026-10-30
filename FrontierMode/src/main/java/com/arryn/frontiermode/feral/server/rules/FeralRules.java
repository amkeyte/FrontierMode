package com.arryn.frontiermode.feral.server.rules;

import com.arryn.satchel.common.tunable.Tunable;
import com.arryn.satchel.common.util.out.OUT;

/**
 * Tunables for Feral (RM_FRO_038 "Martha"). Feral's stat growth is not here: the {@code feral} rung's
 * growth factors are {@code DifficultyRules} Tunables. The numbers are placeholder baselines, not
 * tuned against play, the same "safe baseline, replace later" convention every other rules class in this
 * codebase follows (e.g. {@code PlayerRules}, {@code DefaultBossRules}).
 *
 * <p>Every number here but one is {@link Tunable}-backed (SAT_074, begun in FRO_141): an accessor
 * <b>method</b>, not a field, since a {@code Tunable} is constructed fresh at point of use rather than
 * cached (see that class's own doc). The one plain field is {@link FeralRules#DENSITY_COMPANION_SPREAD_RADIUS}.
 */
public final class FeralRules {

    private FeralRules() {}

    // ------------------------------------------------------------------
    // Density -- extra hostile companions near a hostile that already spawned in the Wilds. It is an
    // approximation of a true spawn-rate multiplier, the same shape Sick Wildlife's density boost has:
    // FrontierMode cannot multiply vanilla's spawn attempts, only piggyback on ones that already
    // happened. The expected count scales with raw distance and is uncapped, see
    // FeralLogic.expectedDensityCount.
    // ------------------------------------------------------------------

    /**
     * Expected extra companions = frontierDistance * this, uncapped. The design target is that player
     * movement becomes unmanageable within 100 blocks of the border, without touching the minimum spawn
     * radius, so density alone has to carry it. At 0.08 the expectation reaches
     * {@link FeralRules#densityMaxPerEvent()} (8) at exactly 100 blocks out, where the ramp and the
     * safety valve meet by construction.
     */
    public static float densityCountScale() {
        return new Tunable("densityCountScale", 0f, 1f, 0.08f).floatValue();
    }

    /**
     * Safety valve, not a design cap: bounds how many companions one spawn event can create at once, so
     * an extreme distance cannot turn one spawn into a lag spike. It is also the intended full-density
     * value at the 100-block design target of {@link FeralRules#densityCountScale()}. How many mobs the game can
     * hold before it lags is not measured.
     */
    public static int densityMaxPerEvent() {
        return new Tunable("densityMaxPerEvent", 0, 50, 8).intValue();
    }

    /**
     * Radius (blocks, both x and z, uniform) a density companion can land from the original spawn's
     * position: a 49x49 area, wide enough that a dense event makes the player maneuver among mobs
     * instead of sidestepping one pile. A placeholder, not derived from anything else.
     */
    public static final int DENSITY_COMPANION_SPREAD_RADIUS = 24;

    // ------------------------------------------------------------------
    // Cosmetic tell -- particles and hurt sounds on already-spawned Feral mobs, mirroring Sick
    // Wildlife's own tell (PlayerRules.WILDLIFE_TELL_*). Purely cosmetic: it never touches health or
    // combat, and only marks a Feral mob as afflicted the way a sick animal is marked.
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
    // Re-entry kill (FRO_111) -- a Feral mob that wanders back inside the Frontier must not survive.
    // The sweep is level-wide, not bound to a player's radius like the cosmetic tell, because the
    // guarantee has to hold everywhere a Feral mob might wander back to.
    // ------------------------------------------------------------------

    /**
     * How often (ticks) each level is swept for Feral mobs that re-entered the Frontier, and for the
     * ambient and pack despawn rules ({@code FeralCleanup.sweep}). A sprinting player loads fresh chunks
     * continuously, which generates ambient Feral spawns quickly, so a short interval keeps the count
     * from overshooting the cap between sweeps.
     */
    public static int reentrySweepIntervalTicks() {
        return new Tunable("reentrySweepIntervalTicks", 1, 200, 20).intValue();
    }

    // ------------------------------------------------------------------
    // Distant despawn -- a Feral mob that is far from every player only occupies a slot in vanilla's
    // hostile mob cap, so it is cleared. The density mechanic can only react to a spawn attempt vanilla
    // makes, and a saturated local cap stops those attempts, so mobs left wandering far away block the
    // cap room a fresh pack needs. A tight band of visible Feral mobs at the border is kept as a visual
    // cue while the player decides whether to cross, and anything past it is cleared. The lingering timer
    // below tolerates a player standing right at the border, where an instant cutoff would make mobs
    // flicker out from under them. The pack and ambient rules further down are what the sweep uses
    // today; the lingering rule is built and switched off (FeralCleanup).
    // ------------------------------------------------------------------

    /**
     * Distance (blocks) from the nearest player beyond which a Feral mob starts its despawn timer (see
     * {@link #despawnLingerTicks()}), freeing its mob-cap slot once the timer fires. Used only by the
     * lingering rule, which is switched off.
     */
    public static float despawnRadius() {
        return new Tunable("despawnRadius", 0f, 200f, 20.0f).floatValue();
    }

    /**
     * How long (ticks) a Feral mob must stay continuously farther than {@link #despawnRadius()} from
     * every player before it is cleared: a lingering window, not an instant cutoff, so a mob does not
     * flicker out and back as a player's distance oscillates around the threshold. 1200 ticks is 60
     * seconds, long enough that the Wilds read as a sustained swarm. Used only by the lingering rule,
     * which is switched off.
     */
    public static int despawnLingerTicks() {
        return new Tunable("despawnLingerTicks", 0, 6000, 1200).intValue();
    }

    // ------------------------------------------------------------------
    // Total population cap -- vanilla's hostile mob cap is 70 per player per dimension, shared by every
    // hostile in the level, not just Feral's own. A static wall of Feral mobs standing at the border,
    // within the despawn radius of the player the whole time, would never time out and could take most of
    // that cap, crowding out ordinary in-border spawns and the room a pack needs.
    // ------------------------------------------------------------------

    /**
     * Ceiling on how many Feral-tagged mobs (ambient natural spawns and pack members combined) may be
     * alive across a level at once: a combined-population safety valve, not a per-mechanic budget (see
     * {@link #maxAlivePackMembers()} for that). Read only by {@code FeralDensity.rollDensityCompanion},
     * which clamps a density roll to the room left under it; that mechanic is switched off
     * ({@code FeralDensity}), so the cap has no effect today. The pack top-up does not read it:
     * {@code FeralPlayerTick.maintainPack} is budgeted by {@link FeralRules#maxAlivePackMembers()}.
     * It is deliberately not part of the tagging decision ({@code FeralTagging.onHostileJoining}), which
     * stays unconditional: every hostile outside the Frontier is Feral. Only whichever mechanic is
     * actively growing the population is throttled once the cap is reached. Well under vanilla's 70-mob
     * hostile cap so real spawn room always remains; the number is not derived from anything else.
     */
    public static int maxAliveFeral() {
        return new Tunable("maxAliveFeral", 0, 70, 40).intValue();
    }

    /**
     * Dedicated ceiling on pack members specifically (the Feral record's {@code pack} flag), counted
     * independently of ambient natural spawns via {@code FeralPresence.packCount}. Ambient Wilds spawning
     * is not capped on its own, so a shared budget would let it use every slot and leave
     * {@code FeralPlayerTick.maintainPack} no room to spawn; this reserves headroom ambient spawns can
     * never crowd out. Twice {@link #packTargetCount()}: enough for one full pack near the player plus a
     * trailing previous batch still clearing out.
     */
    public static int maxAlivePackMembers() {
        return new Tunable("maxAlivePackMembers", 0, 200, 40).intValue();
    }

    // ------------------------------------------------------------------
    // Pack maintenance -- vanilla's natural spawn attempts are too sparse to guarantee an encounter, so
    // while a player is in the Wilds the pack keeps a real group of Feral mobs near that player. See
    // FeralPlayerTick.maintainPack and FeralPackSpawner.spawnMember.
    // ------------------------------------------------------------------

    /**
     * How many Feral mobs {@code FeralPlayerTick.maintainPack} tries to keep within
     * {@link #packMaintainRadius()} of a player who is in the Wilds: a real pack the player has to weave
     * through, not a token few. {@link #maxAlivePackMembers()} is sized as twice this.
     */
    public static int packTargetCount() {
        return new Tunable("packTargetCount", 0, 100, 20).intValue();
    }

    /**
     * Radius (blocks) {@code FeralPlayerTick.maintainPack} counts existing Feral mobs within. It must
     * stay comfortably larger than {@link #packSpawnMaxDistance()}, otherwise a mob can spawn already
     * outside this radius and never count toward its own quota.
     */
    public static float packMaintainRadius() {
        return new Tunable("packMaintainRadius", 8f, 256f, 64f).floatValue();
    }

    /**
     * Distance (blocks) beyond which an ordinary natural Wilds spawn (not a pack member) is discarded
     * outright, see {@code FeralCleanup.despawnIfBeyondAmbientRadius}. Flat and direction-agnostic,
     * unlike {@link #packDespawnRadius()} and {@link #packBehindDespawnRadius()}: an ambient spawn is
     * never part of a deliberate encounter, so it does not get the pack's generous ahead-leash. It reuses
     * {@link #packMaintainRadius()}'s value instead of its own number: a natural spawn outside the zone
     * the pack counts as near the player was never going to be encountered.
     */
    public static float ambientDespawnRadius() {
        // Deliberately tied to packMaintainRadius() rather than its own Tunable -- see the doc above.
        return packMaintainRadius();
    }

    /**
     * Distance (blocks) beyond which a Feral mob still ahead of the player is discarded outright, with
     * no linger, see {@code FeralCleanup.despawnIfBeyondPackRadius}. Deliberately well past
     * {@link #packMaintainRadius()}: the gap between the two keeps a player's distance oscillating near
     * either threshold from constantly spawning and despawning the same mobs, without a per-mob timer.
     * Applies only to mobs ahead of the player; see {@link #packBehindDespawnRadius()} for mobs the
     * player has passed.
     */
    public static float packDespawnRadius() {
        return new Tunable("packDespawnRadius", 8f, 512f, 100f).floatValue();
    }

    /**
     * Distance (blocks) beyond which a Feral mob behind the player is discarded, however far
     * {@link #packDespawnRadius()} lets an ahead mob stay. The speed boost
     * ({@code FeralPackSpawner.applyPackSpeedBoost}) lets a passed mob keep pace from behind, so it can
     * trail the player indefinitely without covering {@code packDespawnRadius()} of raw distance and
     * would hold a slot in {@link FeralRules#maxAlivePackMembers()}'s budget forever. Small on purpose: a
     * short trailing beat after being passed, not an instant vanish, but nowhere near the ahead leash.
     */
    public static float packBehindDespawnRadius() {
        return new Tunable("packBehindDespawnRadius", 0f, 256f, 20f).floatValue();
    }

    /**
     * How often (ticks) {@code FeralPlayerTick.maintainPack} checks and tops up each player's pack. A
     * short interval is a cheap complement to the cone test in
     * {@code FeralPackSpawner.isAheadOfPlayer}, which is what keeps a passed pack from blocking a fresh
     * one.
     */
    public static int packMaintainIntervalTicks() {
        return new Tunable("packMaintainIntervalTicks", 1, 200, 10).intValue();
    }

    // ------------------------------------------------------------------
    // Spawn placement and speed -- a sprinting player outpaces both the maintenance cadence and a
    // zombie's walk speed, so a pack placed around the player's current position ends up behind them.
    // Members are placed in a cone ahead of the player, far enough out to be met, and
    // FeralPackSpawner.isAheadOfPlayer stops a pack the player has run past from blocking a fresh one.
    // See FeralPackSpawner.spawnMember and FeralPlayerTick.maintainPack.
    // ------------------------------------------------------------------

    /**
     * Half-angle (degrees) of the cone pack members spawn within, either side of the player's own yaw.
     * It is also the cone {@code FeralPackSpawner.isAheadOfPlayer} tests membership against, not a plain
     * dot product: a full 180-degree hemisphere lets stale, off-angle mobs keep blocking fresh spawns.
     * Members are force-spawned through a real {@code ForgeEventFactory.onFinalizeSpawn} dispatch, so
     * unlike a natural spawn they are not bound by vanilla's minimum spawn distance from the player.
     */
    public static float packSpawnConeHalfAngleDegrees() {
        return new Tunable("packSpawnConeHalfAngleDegrees", 1f, 180f, 40f).floatValue();
    }

    /** Nearest distance (blocks) a pack member can spawn from the player, within the cone above. */
    public static float packSpawnMinDistance() {
        return new Tunable("packSpawnMinDistance", 0f, 128f, 10f).floatValue();
    }

    /** Farthest distance (blocks) a pack member can spawn from the player, within the cone above. */
    public static float packSpawnMaxDistance() {
        return new Tunable("packSpawnMaxDistance", 1f, 256f, 45f).floatValue();
    }

    /**
     * {@code Attributes.FOLLOW_RANGE} applied to every spawned pack member. Vanilla's zombie default is
     * shorter than {@link #packSpawnMaxDistance()}, so a member landing at the far edge of the cone
     * could spawn already outside its own target-acquisition range and stand there. This is
     * comfortably past {@code packSpawnMaxDistance()} so every member notices the player immediately,
     * wherever in the cone it lands.
     */
    public static float packFollowRange() {
        return new Tunable("packFollowRange", 8f, 256f, 64f).floatValue();
    }

    /**
     * {@code MobEffects.MOVEMENT_SPEED} amplifier applied to every spawned pack member. A plain zombie's
     * walk speed cannot sustain a chase against a sprinting player, so even a well-placed spawn falls
     * behind within seconds without it. Speed VI (amplifier 5) lets the pack close the last stretch and
     * connect instead of trailing at a stable gap. The amplifier is Minecraft's own 0-indexed
     * convention.
     */
    public static int packSpeedAmplifier() {
        return new Tunable("packSpeedAmplifier", 0, 10, 5).intValue();
    }

    /**
     * Duration (ticks) of the speed boost above: generous (30s) rather than tied to engagement time,
     * since there is no cheap signal for "this mob stopped being relevant."
     * {@link #packDespawnRadius()} is what ends a mob's relevance, not this running out.
     */
    public static int packSpeedDurationTicks() {
        return new Tunable("packSpeedDurationTicks", 20, 6000, 600).intValue();
    }

    // ------------------------------------------------------------------
    // Pack Tunable consistency check (FRO_141). The pack Tunables have relationships the docs above
    // state but nothing enforces. A bad CSV edit is warned about, not clamped, so it is diagnosed from the
    // log instead of being silently corrected or silently broken.
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
