package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.core.BlockPos;
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
     * that the disk is real, generated terrain), scores them via {@link #flatnessScore}/
     * {@link #hazardScore}, and returns the winning full position (X, resolved Y, Z) -- already
     * validated, nothing left for materialization to check.
     */
    BlockPos choosePosition(Level level, Border border);

    /**
     * 0 (worst) to 1 (best) flatness score for a candidate position's immediate footprint --
     * "safe baseline, replace later" per border-pregeneration.md, same framing as
     * {@code DefaultBorderRules.GROWTH_FACTOR}.
     */
    double flatnessScore(Level level, BlockPos candidate);

    /**
     * 0 (safe) to 1 (worst) hazard score for a candidate position (lava, deep water, the world
     * floor, ...) -- "safe baseline, replace later," same framing as {@link #flatnessScore}.
     */
    double hazardScore(Level level, BlockPos candidate);

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
}
