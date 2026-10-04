package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;

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
     * that the disk is real, generated terrain), scores them via Siting's
     * flatness and hazard scores, and returns the winning full position (X, resolved Y, Z) -- already
     * validated, nothing left for materialization to check.
     */
    BlockPos choosePosition(Level level, Border border);

    /**
     * Materialization -- called only once the caller (BOSS_JIG's own tick) has already confirmed
     * {@code Level.isLoaded(position)}. {@code position} is already validated, real terrain by
     * this point (chosen and scored by {@link #choosePosition} above) -- no in-place Y-resolution
     * or liquid-column check happens here anymore, per border-pregeneration.md's "What this
     * changes in Boss" section. Spawns the {@code record}'s body, puts it on the Keeper rung of the
     * stat ladder from the Keeper stat tables (boss.md § Keeper stats), and sets it up as a Keeper
     * (fire immunity, sun ward, name). Returns empty only if the entity itself couldn't be created
     * or added (not a terrain problem anymore) -- the caller retries next tick, same fixed
     * {@code position}.
     *
     * <p>Deliberately does not call {@code MobScope.getFor(mob)} or touch {@code BossFixture} --
     * both are Satchel-wiring concerns owned by {@code BossModule}, not a "boss design" decision.
     */
    Optional<Mob> materialize(Level level, BlockPos position, BossRecord record);

    /**
     * The name of the body a boss gets at path position {@code pathPosition}, from the
     * {@code keeper-bodies} table: the authored body through path position 20, a body rolled by
     * pool weight past it. Called once, at record creation.
     */
    String chooseBody(int pathPosition);

    /**
     * The Keeper's size multiplier -- the {@code keeper-size} table through path position 20, its
     * body's size past it (boss.md § Keeper stats). One number driving both the entity's own
     * hitbox/render scale (FRO_148) and the spawn platform's footprint and clearance. Computed from
     * the record's own path position and body, never from a live Border.
     */
    float keeperScale(BossRecord record);

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
     * RM_FRO_029 (Gloria): probability coefficient for the Sentinels placement roll, mirroring
     * {@link #tellParticleCoefficient()}'s exact shape -- applied as
     * {@code placementIntensity x sentinelPlacementCoefficient()} against a [0,1) uniform draw.
     * Safe baseline: 0.15 (sentinels are meant to be a rarer, more meaningful discovery-gradient
     * signal than an environmental tell, not a constant background occurrence). Tuning is Game
     * Designer/playtest territory, same as every other coefficient in this cluster -- see
     * RM_FRO_029's own "which Borders actually turn Sentinels on" open item for the config
     * layer this coefficient sits underneath.
     */
    double sentinelPlacementCoefficient();

    /**
     * RM_FRO_029 (Gloria), revised 2026-09-07 after playtest: the {@code "placement"} roll doesn't
     * normalize distance against the boss's own full {@code border.radius()} -- doing so spread a
     * {@code LINEAR} ramp thin enough across a large border that it read as "no sentinels past a
     * short distance," not a gradient. Instead, the roll's own normalized distance divides by
     * {@code border.radius() * sentinelPlacementRadiusFraction()} -- a smaller reference circle
     * concentric with the border, same shape/formula as before, just compressed so the ramp from
     * "certain" to "never" is actually noticeable as a tell while a player closes in. Clamped like
     * every other normalized distance in this cluster ({@code BorderMath.intensityAt}'s own
     * {@code [0,1]} clamp), so anything beyond this fraction of the radius reads as the curve's own
     * edge value (0 for {@code LINEAR}) -- no sentinels out there at all, by construction. Safe
     * baseline: 0.5 (half the border's radius). Tuning is Game Designer/playtest territory, same as
     * every other value in this cluster -- this one moved twice in one playtest session already.
     */
    double sentinelPlacementRadiusFraction();

    /**
     * Heals {@code mob}, a sentinel that just joined the level, to its full maximum health. Stat
     * scaling runs before vanilla's own setup, which can raise max health without healing (a leader
     * zombie), so the join is the first point at which the final maximum is known.
     */
    void healSentinel(Mob mob);

    /** How many of a defeated boss's sentinels die per {@code BOSS_JIG} tick, so their loot doesn't land in one tick. */
    int sentinelDefeatKillsPerTick();

    /**
     * The share of a Keeper's maximum health that players' own damage must add up to for its death to
     * count as a player's defeat (boss.md § Player credit and non-player death). Safe baseline 0.5.
     */
    double keeperPlayerShare();

    /** How long a player keeps seeing a boss's health bar after they last held its kill credit. */
    int bossBarLingerTicks();

    /**
     * Kills {@code mob}, a sentinel of a defeated boss, as a real vanilla death: ordinary drops
     * always. With a {@code credited} player it is marked as recently hurt by them first, so
     * experience and player-only drops follow; {@code null} kills it uncredited (drops, no XP).
     */
    void killSentinel(Mob mob, ServerPlayer credited);

    /**
     * Horizontal half-width, in blocks, of the box around a boss whose sentinels turn on a player who
     * hits it. A flat number, not tied to the placement ring. See sentinels.md § Defending the boss.
     */
    int sentinelAlertRadius();

    /** How long, in ticks, an alerted sentinel stays on the player after the last hit on its boss. */
    int sentinelAlertMemoryTicks();

    /**
     * Turns {@code sentinel} on {@code player}, replacing any current target, unless vanilla's combat
     * targeting rules exclude the player (creative, spectator, allied). No line-of-sight check.
     *
     * @return true if the sentinel now targets {@code player}
     */
    boolean alertSentinel(Mob sentinel, ServerPlayer player);

    /**
     * An alert on {@code sentinel} lapsed: clears its target if it is still {@code player} and the
     * sentinel can't see them within its own follow range -- which a player in another level never
     * is. One that can see them keeps the target,
     * and vanilla's own targeting and forgiveness take over; any other target is left alone.
     */
    void releaseSentinel(Mob sentinel, ServerPlayer player);

    /**
     * Removes {@code mob} with vanilla's death puff and nothing else: no drops, no XP, no death event.
     * For a doomed sentinel that loaded after its boss was defeated, and for a boss whose defeat was
     * forced ({@code /boss defeat}) or that is found alive after its record was defeated.
     */
    void dismiss(Mob mob);

    /**
     * Whether a spawn of {@code type} can become a sentinel, and so whether the daylight gates may
     * lift for it. {@code MobCategory.MONSTER} is the base test (not {@code instanceof Monster}: Slime
     * and MagmaCube are {@code MONSTER}-category hostiles that don't extend it). Excluded: Phantom,
     * which ignites in sunlight with no helmet check so the sun ward can't protect it; and Slime and
     * MagmaCube, which split on death and would crowd the hostile cap.
     */
    boolean canBeSentinel(EntityType<?> type);

    /**
     * A sentinel's uncapped target scale for its Keeper's path position:
     * {@code 1 + keeperPathPosition x sentinelScaleStep x sentinelScaleRatio}. The ratio keeps a
     * sentinel at a fraction of that growth, so the boss stays the largest thing on the field.
     */
    double sentinelTargetScale(int keeperPathPosition);

    /** The largest scale a single sentinel takes; growth past it becomes extra sentinels instead. */
    double sentinelMaxScale();

    /** How far the fit test steps down from the ceiling toward 1.0 when the full size doesn't fit. */
    double sentinelFitStep();

    /** How much of the scale budget one extra sentinel costs. */
    double sentinelAddCost();

    /** The most extra sentinels one conversion queues. */
    int sentinelMaxAddsPerSpawn();

    /** How far from its parent an extra sentinel is placed, in blocks. */
    int sentinelAddScatterRadius();

    /** Ticks between trail particles per sentinel. Reaches the client through the public fixture. */
    int trailIntervalTicks();

    /** How long each trail particle lives, in ticks. Reaches the client through the public fixture. */
    int trailLifetimeTicks();

    /** How close to the local player a sentinel must be to leave a trail. Reaches the client through the public fixture. */
    int trailRadius();

    /**
     * Equips {@code mob} with the sun ward: an invisible, non-damageable, Curse-of-Binding item in
     * its head slot with a drop chance of 0, so vanilla's sun-ignition check never burns it.
     */
    void equipSunWard(Mob mob);
}
