package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;


/**
 * The base's tunable numbers and the Tell and Sentinel behaviours that still hang off it, mirroring
 * {@code BorderRules}/{@code DefaultBorderRules}' "safe baseline, replace later" shape. The Keeper's own rules --
 * where it goes, what it spawns and is made of, its share and loot -- live in the keeper module
 * ({@code KeeperPlacement}, {@code KeeperBodies}, {@code KeeperSetup}); what is left here leaves with nodes F and G.
 */
public interface BossRules {

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
     * Starting value 0.25 (sentinels are meant to be a rarer, more meaningful discovery-gradient
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
     * The lowest path position of the nearest Keeper at which a spawn near it may become a Sentinel
     * (sentinels.md § The two curves and the conversion decision). Starting value 6, the first Border
     * with room for a real gradient; set it to 1 to test on an early Keeper.
     */
    int sentinelFirstPathPosition();

    /**
     * The most Sentinels one level holds at once, counted over every Keeper's, with queued extra
     * sentinels included. Conversion and the daylight lift stop at it, so a Sentinel zone does not fill
     * the level's hostile spawn pool. Starting value 10.
     */
    int sentinelMaxPerLevel();

    /**
     * Heals {@code mob}, a sentinel that just joined the level, to its full maximum health. Stat
     * scaling runs before vanilla's own setup, which can raise max health without healing (a leader
     * zombie), so the join is the first point at which the final maximum is known.
     */
    void healSentinel(Mob mob);

    /** How many of a defeated boss's sentinels die per {@code BOSS_JIG} tick, so their loot doesn't land in one tick. */
    int sentinelDefeatKillsPerTick();

    /**
     * How far, in blocks, a present boss may be from its recorded position before it is teleported back
     * (border-pregeneration.md § Movement). Safe baseline 40.
     */
    int bossLeashRadius();

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
     * For a sentinel that joined after its boss was defeated, and for a boss whose defeat was
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
}
