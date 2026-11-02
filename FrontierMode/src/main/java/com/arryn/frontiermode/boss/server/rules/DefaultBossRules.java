package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.satchel.common.tunable.Tunable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
//import net.minecraft.server.level.ServerLevel;


/**
 * Default, opinionated implementation of {@link BossRules} -- a safe baseline meant to be
 * replaced by real balance tuning later (Game Designer/playtest territory).
 */
public final class DefaultBossRules implements BossRules {

    // FRO_197: HurtByTargetGoal's own alert conditions -- vanilla's combat rules, no sight test.
    private static final TargetingConditions SENTINEL_ALERT_TARGETING =
            TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

    // ------------------------------------------------------------------
    // FRO_087 (Janice): BossTellFixture tunable baseline values
    // ------------------------------------------------------------------

    @Override
    public int tellTickInterval() {
        // ~1 second at 20 TPS. Safe baseline; playtest territory. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellTickInterval", 1, 200, 20).intValue();
    }

    @Override
    public double tellParticleCoefficient() {
        // Was temporarily maxed to 1.0 as a location-debug aid alongside PARTICLE_COUNT's bump
        // in BossTellFixture (player call, 2026-09-06: "make it a ridiculous amount to see where
        // they're actually showing up") -- guaranteed a particle fire on every tick-interval
        // where intensity > 0, so a walk toward/around a boss revealed the tell radius without
        // waiting on RNG. Scatter shape confirmed good ("that's about where I want it"), so
        // dialed back to the original 70% tuned baseline -- roll is intensity x this coefficient,
        // peaking at 1.0 right at the boss and fading to 0.0 at the border edge. Playtest
        // territory, tune further if it now reads too sparse without the debug multiplier.
        // FRO_142: Tunable-backed (SAT_074) -- [0,1] bound, this coefficient is a probability roll.
        return new Tunable("tellParticleCoefficient", 0f, 1f, 0.7f).floatValue();
    }

    @Override
    public double tellSoundCoefficient() {
        // 5% roll at maximum intensity -- sounds rarer than particles by design.
        // Safe baseline; playtest territory. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellSoundCoefficient", 0f, 1f, 0.05f).floatValue();
    }

    @Override
    public int tellSoundCooldownTicks() {
        // 30 seconds at 20 TPS -- see BossRules#tellSoundCooldownTicks's own doc for why this
        // exists (round-robin, 4x ~30s clips, project owner request 2026-09-08). Safe baseline;
        // playtest territory, and worth revisiting per-clip if the 4 supplied sounds aren't all
        // the same length. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellSoundCooldownTicks", 0, 6000, 600).intValue();
    }

    // ------------------------------------------------------------------
    // RM_FRO_029 (Gloria): BossSentinelsFixture tunable baseline values
    // ------------------------------------------------------------------

    // The sentinel numbers are Tunable-backed, constructed inside their own accessor methods rather
    // than cached as fields.

    @Override
    public double sentinelPlacementCoefficient() {
        // Max chance per spawn attempt at intensity 1.0 (right on top of the boss), decaying with
        // placementIntensity. Low, so Sentinels disperse across the ring instead of clumping and do not
        // fill the level's hostile spawn pool. A starting value, tuned in play. [0,1] bound -- a probability.
        return new Tunable("sentinelPlacementCoefficient", 0f, 1f, 0.25f).floatValue();
    }

    @Override
    public double sentinelPlacementRadiusFraction() {
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

    @Override
    public int sentinelFirstPathPosition() {
        return new Tunable("sentinelFirstPathPosition", 1, 1000, 6).intValue();
    }

    @Override
    public int sentinelMaxPerLevel() {
        return new Tunable("sentinelMaxPerLevel", 1, 1000, 10).intValue();
    }

    @Override
    public void healSentinel(Mob mob) {
        mob.setHealth(mob.getMaxHealth());
    }

    @Override
    public int sentinelDefeatKillsPerTick() {
        return new Tunable("sentinelDefeatKillsPerTick", 1, 100, 5).intValue();
    }

    @Override
    public int bossLeashRadius() {
        return new Tunable("bossLeashRadius", 8, 512, 40).intValue();
    }

    @Override
    public int bossBarLingerTicks() {
        return new Tunable("bossBarLingerTicks", 1, 72000, 600).intValue();
    }

    @Override
    public void killSentinel(Mob mob, ServerPlayer credited) {
        if (credited != null) {
            mob.setLastHurtByPlayer(credited);
        }
        mob.kill();
    }

    // FRO_197: sentinels defend the boss (sentinels.md § Defending the boss).
    @Override
    public int sentinelAlertRadius() {
        return new Tunable("sentinelAlertRadius", 4, 64, 24).intValue();
    }

    @Override
    public int sentinelAlertMemoryTicks() {
        return new Tunable("sentinelAlertMemoryTicks", 20, 1200, 200).intValue();
    }

    @Override
    public boolean alertSentinel(Mob sentinel, ServerPlayer player) {
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

    @Override
    public void releaseSentinel(Mob sentinel, ServerPlayer player) {
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

    @Override
    public void dismiss(Mob mob) {
        // Entity event 60: vanilla's death puff.
        mob.level().broadcastEntityEvent(mob, (byte) 60);
        mob.discard();
    }

    @Override
    public boolean canBeSentinel(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER
                && type != EntityType.PHANTOM
                && type != EntityType.SLIME
                && type != EntityType.MAGMA_CUBE;
    }

    // Sentinel size and adds: starting values for playtest (sentinels.md, "Size and adds").
    @Override
    public double sentinelTargetScale(int keeperPathPosition) {
        // The Sentinels' own size curve (the per-step growth bosses used before their size became a
        // table), kept so Sentinel sizes don't follow a table built for Keepers.
        double step = new Tunable("sentinelScaleStep", 0f, 1f, 0.1f).floatValue();
        double ratio = new Tunable("sentinelScaleRatio", 0f, 1f, 0.5f).floatValue();
        return 1.0 + Math.max(keeperPathPosition, 0) * step * ratio;
    }

    @Override
    public double sentinelMaxScale() {
        return new Tunable("sentinelMaxScale", 1f, 5f, 1.5f).floatValue();
    }

    @Override
    public double sentinelFitStep() {
        return new Tunable("sentinelFitStep", 0.01f, 1f, 0.1f).floatValue();
    }

    @Override
    public double sentinelAddCost() {
        return new Tunable("sentinelAddCost", 0.05f, 10f, 0.5f).floatValue();
    }

    @Override
    public int sentinelMaxAddsPerSpawn() {
        return new Tunable("sentinelMaxAddsPerSpawn", 0, 20, 3).intValue();
    }

    @Override
    public int sentinelAddScatterRadius() {
        return new Tunable("sentinelAddScatterRadius", 1, 32, 4).intValue();
    }

    // The trail's three numbers. Tunables are server-only, so BOSS_JIG's tick copies them into
    // SentinelPublicFixture, the one channel from server to client for sentinels.
    @Override
    public int trailIntervalTicks() {
        return new Tunable("trailIntervalTicks", 1, 200, 4).intValue();
    }

    @Override
    public int trailLifetimeTicks() {
        return new Tunable("trailLifetimeTicks", 1, 600, 30).intValue();
    }

    @Override
    public int trailRadius() {
        return new Tunable("trailRadius", 1, 128, 24).intValue();
    }
}
