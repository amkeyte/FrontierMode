package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy interface for Boss's own two genuinely separate mechanisms -- position and
 * materialization -- mirroring {@code BorderRules}/{@code DefaultBorderRules}' existing
 * "safe baseline, replace later" shape. See wiki/frontiermode/architecture/boss.md's "Spawn
 * algorithm" section: these are not one fused loop.
 *
 * <p><b>Position, revised by Border Pregeneration:</b> no longer commits the instant a
 * {@code BossFixture} record is created -- {@link #choosePosition} is only ever called once
 * {@code BorderAPI.isPregenReady()} has returned true for {@code border}'s own disk (real,
 * validated terrain), and scores several candidates rather than picking one blind column. See
 * wiki/frontiermode/architecture/border-pregeneration.md#worked-example-boss-placement-revised.
 */
public interface BossRules {

    /**
     * Position -- called only once the whole target {@code border}'s disk is already
     * pregenerated. Samples several chunk-center candidates within {@code border}'s disk
     * ({@code BorderMath.randomPointInDisk}), resolves each candidate's real ground Y (safe now
     * that the disk is real, generated terrain), scores them via {@link #flatnessScore}/
     * {@link #hazardScore}, and returns the winning full position (X, resolved Y, Z) -- already
     * validated, nothing left for materialization to check.
     */
    BlockPos choosePosition(Level level, Border border);

    /**
     * 0 (worst) to 1 (best) flatness score for a candidate position's immediate footprint --
     * "safe baseline, replace later" per border-pregeneration.md.
     */
    double flatnessScore(Level level, BlockPos candidate);

    /**
     * 0 (safe) to 1 (worst) hazard score for a candidate position (lava, deep water, the world
     * floor, ...) -- "safe baseline, replace later," same framing as {@link #flatnessScore}.
     */
    double hazardScore(Level level, BlockPos candidate);

    /**
     * FRO_120 (Cairn): footprint-mode terrain-suitability check over a {@code size × size} area
     * centered on {@code center}, reusing the identical {@link #flatnessScore}/{@link
     * #hazardScore} logic {@link #choosePosition} already scores a single column with -- one
     * shared rule across point-mode and footprint-mode, not two independently hand-rolled
     * versions that could drift apart. See
     * wiki/frontiermode/architecture/boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules
     * and wiki/frontiermode/architecture/cairn.md#placement.
     *
     * <p>Unlike {@link #choosePosition}'s "score several candidates, pick the best" shape, this
     * is a plain boolean pass/fail for one candidate -- Cairn's own placement (the first
     * consumer) resamples a fresh candidate on failure rather than scoring several at once, per
     * cairn.md's own "a dev stand-in for now" framing.
     *
     * @param clearance required vertical clearance above each column's resolved ground, in
     *                  blocks -- one shared number across every footprint consumer (5, per
     *                  boss.md's own spec, sized to cover both a boss mob's standing room and
     *                  Cairn's 3-tall pillar plus a spare block above the cap).
     */
    boolean footprintSuitable(Level level, BlockPos center, int size, int clearance);

    /**
     * Materialization -- called only once the caller (BOSS_JIG's own tick) has already confirmed
     * {@code Level.isLoaded(position)}. {@code position} is already validated, real terrain by
     * this point (chosen and scored by {@link #choosePosition} above) -- no in-place Y-resolution
     * or liquid-column check happens here anymore, per border-pregeneration.md's "What this
     * changes in Boss" section. Spawns the vanilla mob for {@code layer}, applies placeholder
     * stat scaling, and attaches a visible marker so "no discovery aids" (Tier 1) still means
     * "findable by looking." Returns empty only if the entity itself couldn't be created or
     * added (not a terrain problem anymore) -- the caller retries next tick, same fixed
     * {@code position}.
     *
     * <p>Deliberately does not call {@code MobScope.getFor(mob)} or touch {@code BossFixture} --
     * both are Satchel-wiring concerns owned by {@code BossModule}, not a "boss design" decision.
     */
    Optional<Mob> materialize(Level level, BlockPos position, int layer);

    /**
     * FRO_148: the boss's size multiplier for {@code layer} -- {@code 1.0 + layer * perLayer},
     * one number driving both the entity's own hitbox/render scale and (boss.md's "Spawn
     * platform") the platform's footprint and clearance. Computed once at attach from the record's
     * own layer, never re-read from a live Border. Safe baseline: {@code perLayer = 0.1}.
     */
    float scaleForLayer(int layer);

    /**
     * FRO_160: ticks between a non-player death returning a boss to unmaterialized and the
     * materialization tick being allowed to respawn it at its recorded position. Safe baseline:
     * 100 ticks (5 seconds). Tuning is playtest territory.
     */
    int respawnDelayTicks();

    /**
     * FRO_160: how many consecutive non-player deaths a single boss record may take before
     * auto-respawn is suspended (a loop guard, not a balance lever). Safe baseline: 5.
     */
    int maxConsecutiveNonPlayerDeaths();

    /**
     * FRO_087 (Janice): cadence for the tell-pass in {@code BossTellFixture} -- how many server
     * ticks between each per-player intensity evaluation and particle/sound roll. Safe baseline:
     * 20 ticks (~1 second). Tuning is playtest territory.
     */
    int tellTickInterval();

    /**
     * FRO_087 (Janice): probability coefficient for the tell particle roll. Applied as
     * {@code intensity × tellParticleCoefficient()} against a [0,1) uniform random draw -- so a
     * coefficient of 0.3 means a player at the boss position itself gets a 30% roll each
     * interval. Safe baseline: 0.3. Tuning is playtest territory.
     */
    double tellParticleCoefficient();

    /**
     * FRO_087 (Janice): probability coefficient for the tell sound roll. Independent of the
     * particle roll -- same intensity value, separate draw. Safe baseline: 0.05 (much rarer than
     * particle). Tuning is playtest territory.
     */
    double tellSoundCoefficient();

    /**
     * RM_FRO_037 (Curtis, boss-audio-tell follow-up, 2026-09-08): hard per-boss cooldown (in
     * ticks) on the tell sound roll, on top of {@link #tellSoundCoefficient()}'s own probability.
     * Needed once the tell sound stopped being a sub-second blip: {@link #tellTickInterval()}
     * (~1s) combined with a 5% roll meant a several-seconds-long clip could easily get
     * re-triggered -- by the same player rolling again, or by a different player near the same
     * boss rolling independently -- before the previous one finished, stacking overlapping
     * playback instead of a clean one-at-a-time rotation. This cooldown is evaluated first: only
     * once it's elapsed for a given boss does the probability roll even get a chance to fire
     * again for that boss. Safe baseline: 600 ticks (30 seconds), sized to this ticket's own
     * project-owner-supplied clip length -- revisit if any clip in the round-robin set ends up a
     * different length than the others.
     */
    int tellSoundCooldownTicks();

    /**
     * RM_FRO_029 (Gloria): probability coefficient for the Guardian Mobs placement roll, mirroring
     * {@link #tellParticleCoefficient()}'s exact shape -- applied as
     * {@code placementIntensity x guardianPlacementCoefficient()} against a [0,1) uniform draw.
     * Safe baseline: 0.15 (guardians are meant to be a rarer, more meaningful discovery-gradient
     * signal than an environmental tell, not a constant background occurrence). Tuning is Game
     * Designer/playtest territory, same as every other coefficient in this cluster -- see
     * RM_FRO_029's own "which Borders actually turn Guardian Mobs on" open item for the config
     * layer this coefficient sits underneath.
     */
    double guardianPlacementCoefficient();

    /**
     * RM_FRO_029 (Gloria), revised 2026-09-07 after playtest: the {@code "placement"} roll doesn't
     * normalize distance against the boss's own full {@code border.radius()} -- doing so spread a
     * {@code LINEAR} ramp thin enough across a large border that it read as "no guardians past a
     * short distance," not a gradient. Instead, the roll's own normalized distance divides by
     * {@code border.radius() * guardianPlacementRadiusFraction()} -- a smaller reference circle
     * concentric with the border, same shape/formula as before, just compressed so the ramp from
     * "certain" to "never" is actually noticeable as a tell while a player closes in. Clamped like
     * every other normalized distance in this cluster ({@code BorderMath.intensityAt}'s own
     * {@code [0,1]} clamp), so anything beyond this fraction of the radius reads as the curve's own
     * edge value (0 for {@code LINEAR}) -- no guardians out there at all, by construction. Safe
     * baseline: 0.5 (half the border's radius). Tuning is Game Designer/playtest territory, same as
     * every other value in this cluster -- this one moved twice in one playtest session already.
     */
    double guardianPlacementRadiusFraction();

    /**
     * Heals {@code mob}, a guardian that just joined the level, to its full maximum health. Stat
     * scaling runs before vanilla's own setup, which can raise max health without healing (a leader
     * zombie), so the join is the first point at which the final maximum is known.
     */
    void healGuardian(Mob mob);

    /** How many of a defeated boss's guardians die per {@code BOSS_JIG} tick, so their loot doesn't land in one tick. */
    int guardianDefeatKillsPerTick();

    /** A boss gets a health bar only at this maximum health or more. See boss.md § Health bar. */
    float bossBarMinHealth();

    /** How long a player keeps seeing a boss's health bar after they last held its kill credit. */
    int bossBarLingerTicks();

    /**
     * Kills {@code mob}, a guardian of a defeated boss, as a real vanilla death: ordinary drops
     * always. With a {@code credited} player it is marked as recently hurt by them first, so
     * experience and player-only drops follow; {@code null} kills it uncredited (drops, no XP).
     */
    void killGuardian(Mob mob, ServerPlayer credited);

    /**
     * Removes {@code mob} with vanilla's death puff and nothing else: no drops, no XP, no death event.
     * For a doomed guardian that loaded after its boss was defeated, and for a boss whose defeat was
     * forced ({@code /boss defeat}) or that is found alive after its record was defeated.
     */
    void dismiss(Mob mob);

    /**
     * Whether a spawn of {@code type} can become a guardian, and so whether the daylight gates may
     * lift for it. {@code MobCategory.MONSTER} is the base test (not {@code instanceof Monster}: Slime
     * and MagmaCube are {@code MONSTER}-category hostiles that don't extend it). Excluded: Phantom,
     * which ignites in sunlight with no helmet check so the sun ward can't protect it; and Slime and
     * MagmaCube, which split on death and would crowd the hostile cap.
     */
    boolean canBeGuardian(EntityType<?> type);

    /**
     * A guardian's uncapped target scale for its boss's {@code layer}:
     * {@code 1 + layer x scalePerLayer x guardianScaleRatio}. The ratio keeps a guardian at a
     * fraction of its boss's growth, so the boss stays the largest thing on the field at every layer.
     */
    double guardianTargetScale(int layer);

    /** The largest scale a single guardian takes; growth past it becomes extra guardians instead. */
    double guardianMaxScale();

    /** How far the fit test steps down from the ceiling toward 1.0 when the full size doesn't fit. */
    double guardianFitStep();

    /** How much of the scale budget one extra guardian costs. */
    double guardianAddCost();

    /** The most extra guardians one conversion queues. */
    int guardianMaxAddsPerSpawn();

    /** How far from its parent an extra guardian is placed, in blocks. */
    int guardianAddScatterRadius();

    /**
     * Puts a converted guardian on the shared guardian team. The team carries color only (what
     * makes a glowing guardian's outline purple); membership has no visible effect while glow is off.
     */
    void joinGuardianTeam(Mob mob, ServerLevel level);

    /** Removes {@code mobUuid} from the guardian team -- called when its record is deleted. */
    void leaveGuardianTeam(UUID mobUuid, ServerLevel level);

    /** Ticks between trail particles per guardian. Reaches the client through the public fixture. */
    int trailIntervalTicks();

    /** How long each trail particle lives, in ticks. Reaches the client through the public fixture. */
    int trailLifetimeTicks();

    /** How close to the local player a guardian must be to leave a trail. Reaches the client through the public fixture. */
    int trailRadius();

    /**
     * Equips {@code mob} with the sun ward: an invisible, non-damageable, Curse-of-Binding item in
     * its head slot with a drop chance of 0, so vanilla's sun-ignition check never burns it.
     */
    void equipSunWard(Mob mob);

    /**
     * RM_FRO_029 (Gloria): applies stat scaling keyed off {@code difficultyIntensity} rather than
     * layer -- mirrors {@code DefaultBossRules.applyStatScaling()} directly, same "safe baseline,
     * replace later" framing as {@link #tellParticleCoefficient()} and every other tunable in this
     * cluster. Ships in v1 alongside the marker, per the same 2026-09-07 ruling -- not deferred.
     */
    void applyGuardianStatScaling(Mob mob, double difficultyIntensity);
}
