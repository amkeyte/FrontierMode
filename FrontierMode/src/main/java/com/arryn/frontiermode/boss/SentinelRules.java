package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.satchel.common.tunable.Tunable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/**
 * The Sentinels' numbers and behaviours (wiki/frontiermode/architecture/sentinels.md): placement, the per-level cap,
 * heal, kill, alert and release, which spawns can convert, size, adds and the trail. They sat on the boss rules
 * classes; those ended with node F (FRO_289), and there is no {@code sentinel} module yet to hold them, so they are
 * here, unchanged, for node G to carry into it. A Tunable's key includes its class, so these keys are
 * {@code SentinelRules:<name>}. The numbers are Tunable-backed, constructed inside their own accessor methods rather
 * than cached as fields.
 */
public final class SentinelRules {

    // FRO_197: HurtByTargetGoal's own alert conditions -- vanilla's combat rules, no sight test.
    private static final TargetingConditions SENTINEL_ALERT_TARGETING =
            TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

    private SentinelRules() {
    }

    /**
     * RM_FRO_029 (Gloria): probability coefficient for the Sentinels placement roll -- applied as
     * {@code placementIntensity x sentinelPlacementCoefficient()} against a [0,1) uniform draw.
     */
    public static double sentinelPlacementCoefficient() {
        // Max chance per spawn attempt at intensity 1.0 (right on top of the boss), decaying with
        // placementIntensity. Low, so Sentinels disperse across the ring instead of clumping and do not
        // fill the level's hostile spawn pool. A starting value, tuned in play. [0,1] bound -- a probability.
        return new Tunable("sentinelPlacementCoefficient", 0f, 1f, 0.25f).floatValue();
    }

    /**
     * RM_FRO_029 (Gloria), revised 2026-09-07 after playtest: the {@code "placement"} roll divides its normalized
     * distance by {@code border.radius() * sentinelPlacementRadiusFraction()}, a smaller reference circle concentric
     * with the border, so the ramp from "certain" to "never" is actually noticeable as a tell while a player closes in.
     */
    public static double sentinelPlacementRadiusFraction() {
        // 2026-09-07 playtest, revised twice same session: started at the full border radius (read as
        // "clustered right on the boss, nothing beyond ~30 blocks" on a large border -- the LINEAR
        // ramp was mathematically correct but imperceptibly thin across the whole disc); briefly tried
        // FLAT/uniform across the whole border (reverted -- "doesn't act as a tell at all," no gradient
        // to notice at all). Settled: same LINEAR formula, same direction (climbs toward the boss), but
        // normalized against half the border's own radius instead of the whole thing -- a real,
        // noticeable ramp concentrated in a smaller ring around the boss. Safe baseline; playtest
        // territory, same as sentinelPlacementCoefficient above. [0,1] bound -- a fraction of radius.
        return new Tunable("sentinelPlacementRadiusFraction", 0f, 1f, 0.5f).floatValue();
    }

    /**
     * The lowest path position of the nearest Keeper at which a spawn near it may become a Sentinel
     * (sentinels.md § The two curves and the conversion decision). Starting value 6, the first Border with room for a
     * real gradient; set it to 1 to test on an early Keeper.
     */
    public static int sentinelFirstPathPosition() {
        return new Tunable("sentinelFirstPathPosition", 1, 1000, 6).intValue();
    }

    /**
     * The most Sentinels one level holds at once, counted over every Keeper's, with queued extra sentinels included.
     * Conversion and the daylight lift stop at it, so a Sentinel zone does not fill the level's hostile spawn pool.
     * Starting value 10.
     */
    public static int sentinelMaxPerLevel() {
        return new Tunable("sentinelMaxPerLevel", 1, 1000, 10).intValue();
    }

    /**
     * Heals {@code mob}, a sentinel that just joined the level, to its full maximum health. Stat scaling runs before
     * vanilla's own setup, which can raise max health without healing (a leader zombie), so the join is the first
     * point at which the final maximum is known.
     */
    public static void healSentinel(Mob mob) {
        mob.setHealth(mob.getMaxHealth());
    }

    /** How many of a defeated boss's sentinels die per {@code BOSS_JIG} tick, so their loot doesn't land in one tick. */
    public static int sentinelDefeatKillsPerTick() {
        return new Tunable("sentinelDefeatKillsPerTick", 1, 100, 5).intValue();
    }

    /**
     * Kills {@code mob}, a sentinel of a defeated boss, as a real vanilla death: ordinary drops always. With a
     * {@code credited} player it is marked as recently hurt by them first, so experience and player-only drops follow;
     * {@code null} kills it uncredited (drops, no XP).
     */
    public static void killSentinel(Mob mob, ServerPlayer credited) {
        if (credited != null) {
            mob.setLastHurtByPlayer(credited);
        }
        mob.kill();
    }

    /**
     * FRO_197: sentinels defend the boss (sentinels.md § Defending the boss). Horizontal half-width, in blocks, of the
     * box around a boss whose sentinels turn on a player who hits it. A flat number, not tied to the placement ring.
     */
    public static int sentinelAlertRadius() {
        return new Tunable("sentinelAlertRadius", 4, 64, 24).intValue();
    }

    /** How long, in ticks, an alerted sentinel stays on the player after the last hit on its boss. */
    public static int sentinelAlertMemoryTicks() {
        return new Tunable("sentinelAlertMemoryTicks", 20, 1200, 200).intValue();
    }

    /**
     * Turns {@code sentinel} on {@code player}, replacing any current target, unless vanilla's combat targeting rules
     * exclude the player (creative, spectator, allied). No line-of-sight check.
     *
     * @return true if the sentinel now targets {@code player}
     */
    public static boolean alertSentinel(Mob sentinel, ServerPlayer player) {
        // Allies come first: a Sentinel already fighting an ally is left to it, so no alert window is
        // recorded; once the ally is gone or out of sight the next hit on the boss alerts it as usual.
        if (sentinel.getTarget() != null && DifficultyAPI.isAlly(sentinel.getTarget())) {
            return false;
        }
        if (!SENTINEL_ALERT_TARGETING.test(sentinel, player)) {
            return false;
        }
        sentinel.setTarget(player);
        return true;
    }

    /**
     * An alert on {@code sentinel} lapsed: clears its target if it is still {@code player} and the sentinel can't see
     * them within its own follow range -- which a player in another level never is. One that can see them keeps the
     * target, and vanilla's own targeting and forgiveness take over; any other target is left alone.
     */
    public static void releaseSentinel(Mob sentinel, ServerPlayer player) {
        if (sentinel.getTarget() != player) {
            return;
        }
        var followRange = sentinel.getAttribute(Attributes.FOLLOW_RANGE);
        double range = followRange == null ? 0.0 : followRange.getValue();
        // Same level first: after a portal, distance and sight would be measured against the
        // player's coordinates in the other dimension and can come out true.
        boolean canSee = player.level() == sentinel.level()
                && sentinel.distanceToSqr(player) <= range * range
                && sentinel.getSensing().hasLineOfSight(player);
        if (!canSee) {
            sentinel.setTarget(null);
        }
    }

    /**
     * Whether a spawn of {@code type} can become a sentinel, and so whether the daylight gates may lift for it.
     * {@code MobCategory.MONSTER} is the base test (not {@code instanceof Monster}: Slime and MagmaCube are
     * {@code MONSTER}-category hostiles that don't extend it). Excluded: Phantom, which ignites in sunlight with no
     * helmet check so the sun ward can't protect it; and Slime and MagmaCube, which split on death and would crowd the
     * hostile cap.
     */
    public static boolean canBeSentinel(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER
                && type != EntityType.PHANTOM
                && type != EntityType.SLIME
                && type != EntityType.MAGMA_CUBE;
    }

    // Sentinel size and adds: starting values for playtest (sentinels.md, "Size and adds").

    /**
     * A sentinel's uncapped target scale for its Keeper's path position:
     * {@code 1 + keeperPathPosition x sentinelScaleStep x sentinelScaleRatio}. The ratio keeps a sentinel at a fraction
     * of that growth, so the boss stays the largest thing on the field.
     */
    public static double sentinelTargetScale(int keeperPathPosition) {
        // The Sentinels' own size curve (the per-step growth bosses used before their size became a
        // table), kept so Sentinel sizes don't follow a table built for Keepers.
        double step = new Tunable("sentinelScaleStep", 0f, 1f, 0.1f).floatValue();
        double ratio = new Tunable("sentinelScaleRatio", 0f, 1f, 0.5f).floatValue();
        return 1.0 + Math.max(keeperPathPosition, 0) * step * ratio;
    }

    /** The largest scale a single sentinel takes; growth past it becomes extra sentinels instead. */
    public static double sentinelMaxScale() {
        return new Tunable("sentinelMaxScale", 1f, 5f, 1.5f).floatValue();
    }

    /** How far the fit test steps down from the ceiling toward 1.0 when the full size doesn't fit. */
    public static double sentinelFitStep() {
        return new Tunable("sentinelFitStep", 0.01f, 1f, 0.1f).floatValue();
    }

    /** How much of the scale budget one extra sentinel costs. */
    public static double sentinelAddCost() {
        return new Tunable("sentinelAddCost", 0.05f, 10f, 0.5f).floatValue();
    }

    /** The most extra sentinels one conversion queues. */
    public static int sentinelMaxAddsPerSpawn() {
        return new Tunable("sentinelMaxAddsPerSpawn", 0, 20, 3).intValue();
    }

    /** How far from its parent an extra sentinel is placed, in blocks. */
    public static int sentinelAddScatterRadius() {
        return new Tunable("sentinelAddScatterRadius", 1, 32, 4).intValue();
    }

    // The trail's three numbers. Tunables are server-only, so BOSS_JIG's tick copies them into
    // SentinelPublicFixture, the one channel from server to client for sentinels.

    /** Ticks between trail particles per sentinel. Reaches the client through the public fixture. */
    public static int trailIntervalTicks() {
        return new Tunable("trailIntervalTicks", 1, 200, 4).intValue();
    }

    /** How long each trail particle lives, in ticks. Reaches the client through the public fixture. */
    public static int trailLifetimeTicks() {
        return new Tunable("trailLifetimeTicks", 1, 600, 30).intValue();
    }

    /** How close to the local player a sentinel must be to leave a trail. Reaches the client through the public fixture. */
    public static int trailRadius() {
        return new Tunable("trailRadius", 1, 128, 24).intValue();
    }
}
