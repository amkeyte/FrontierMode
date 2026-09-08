package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
     * Project owner request (2026-09-08): a live debug toggle for whether {@code tagVisibly}
     * (Boss materialize) and {@code tagGuardian} (Guardian Mobs tagging) apply
     * {@code Entity#setGlowingTag(true)} to the mob they're tagging. Unlike every other method on
     * this interface, this pair is mutable, real-time state rather than a fixed strategy/baseline
     * -- deliberately still routed through {@code BossRules}/{@code BossRulesFacet} rather than a
     * standalone static field (the way {@code WorldBordersRenderer}'s own ring-visibility toggle
     * is), because this one needs to be reachable from a SERVER command (glow is real synced
     * entity state, not a purely client-side render decision) and {@code BossRulesFacet} is
     * already the one existing path {@code BossCommandHandler} and {@code BossTellFixture} both
     * use to reach the live {@code DefaultBossRules} instance. Default {@code true} -- matches
     * this codebase's behavior before the toggle existed.
     */
    boolean glowEnabled();

    /** See {@link #glowEnabled()}. */
    void setGlowEnabled(boolean value);

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
     * RM_FRO_029 (Gloria): buckets a rolled guardian's {@code difficultyIntensity} (from the
     * border's {@code "difficulty"}-purpose curve) into a tier -- the same pluggable-strategy
     * territory {@link #materialize}'s layer-to-mob-table lookup already occupies. Safe baseline:
     * a flat linear bucket count, "safe baseline, replace later." Feeds both {@link #tagGuardian}'s
     * displayed tier and, indirectly, {@link #applyGuardianStatScaling}.
     */
    int guardianTier(double difficultyIntensity);

    /**
     * RM_FRO_029 (Gloria): applies the visible guardian marker -- name tag
     * ({@code "GM-<tier>[<intensity>%]"}) and a shared, team-colored glow -- mirroring
     * {@code DefaultBossRules.tagVisibly()} directly, per
     * wiki/frontiermode/architecture/guardian-mobs.md's "Marker and stat scaling" ruling
     * (2026-09-07): both the name tag and the glow ship together, not just the name-tag half.
     */
    void tagGuardian(Mob mob, int tier, double difficultyIntensity, ServerLevel level);

    /**
     * RM_FRO_029 (Gloria): applies stat scaling keyed off {@code difficultyIntensity} rather than
     * layer -- mirrors {@code DefaultBossRules.applyStatScaling()} directly, same "safe baseline,
     * replace later" framing as {@link #tellParticleCoefficient()} and every other tunable in this
     * cluster. Ships in v1 alongside the marker, per the same 2026-09-07 ruling -- not deferred.
     */
    void applyGuardianStatScaling(Mob mob, double difficultyIntensity);
}
