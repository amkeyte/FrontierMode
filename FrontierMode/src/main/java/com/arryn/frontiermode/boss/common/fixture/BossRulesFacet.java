package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.server.rules.BossRules;
import com.arryn.frontiermode.boss.server.rules.DefaultBossRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * FRO_081: Boss's pluggable-strategy facet, mirroring {@code BordersRulesFacet}'s shape -- wraps
 * the {@link BossRules}/{@link DefaultBossRules} strategy object (moved here from
 * {@code BossModule}'s own private {@code static final BossRules RULES} field), exposing position
 * validation (flatness/hazard scoring) and materialization as this fixture's RULES surface.
 *
 * <p>The wrapped {@link BossRules} instance is declared {@code static} on this class -- one
 * instance shared by every level's {@code BossFixture} (and so every level's own
 * {@code BossRulesFacet}), not one freshly constructed per facet/level. This preserves the exact
 * single-shared-instance (and single shared {@code RandomSource}) semantics
 * {@code BossModule.RULES} always had -- moving the field here without also making it
 * {@code static} would have quietly given every level its own independent
 * {@code DefaultBossRules}/RNG instead, a behavior change this ticket's "move, don't rewrite"
 * constraint rules out.
 */
public final class BossRulesFacet {
    private static final BossRules RULES = new DefaultBossRules();

    BossRulesFacet(BossFixture fixture) {
    }


    public BlockPos choosePosition(Level level, Border border) {
        return RULES.choosePosition(level, border);
    }

    /**
     * See {@link BossRules#flatnessScore(Level, BlockPos)}.
     */
    public double flatnessScore(Level level, BlockPos candidate) {
        return RULES.flatnessScore(level, candidate);
    }

    /**
     * See {@link BossRules#hazardScore(Level, BlockPos)}.
     */
    public double hazardScore(Level level, BlockPos candidate) {
        return RULES.hazardScore(level, candidate);
    }

    /**
     * See {@link BossRules#materialize(Level, BlockPos, int)}.
     */
    public Optional<Mob> materialize(Level level, BlockPos position, int layer) {
        return RULES.materialize(level, position, layer);
    }

    /**
     * FRO_120 (Cairn): the shared footprint-mode terrain check, reached via
     * {@code BossAPI.RULES(Level)} from outside the {@code boss} package -- see
     * {@link BossRules#footprintSuitable(Level, BlockPos, int, int)}.
     */
    public boolean footprintSuitable(Level level, BlockPos center, int size, int clearance) {
        return RULES.footprintSuitable(level, center, size, clearance);
    }

    /**
     * See {@link BossRules#scaleForLayer(int)}.
     */
    public float scaleForLayer(int layer) {
        return RULES.scaleForLayer(layer);
    }

    /**
     * See {@link BossRules#respawnDelayTicks()}.
     */
    public int respawnDelayTicks() {
        return RULES.respawnDelayTicks();
    }

    /**
     * See {@link BossRules#maxConsecutiveNonPlayerDeaths()}.
     */
    public int maxConsecutiveNonPlayerDeaths() {
        return RULES.maxConsecutiveNonPlayerDeaths();
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#tellTickInterval()}.
     */
    public int tellTickInterval() {
        return RULES.tellTickInterval();
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#tellParticleCoefficient()}.
     */
    public double tellParticleCoefficient() {
        return RULES.tellParticleCoefficient();
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#tellSoundCoefficient()}.
     */
    public double tellSoundCoefficient() {
        return RULES.tellSoundCoefficient();
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#tellSoundCooldownTicks()}.
     * RM_FRO_037 boss-audio-tell follow-up (2026-09-08): missed adding this passthrough when the
     * interface/impl methods were added -- this facet is the only path {@code BossTellFixture}
     * actually calls through ({@code bossFixture.RULES} is a {@code BossRulesFacet}, not the raw
     * {@link com.arryn.frontiermode.boss.server.rules.BossRules} instance), so the real compile
     * error ("cannot find symbol ... location: variable RULES of type BossRulesFacet") was exactly
     * this gap, not a problem with the interface/impl themselves.
     */
    public int tellSoundCooldownTicks() {
        return RULES.tellSoundCooldownTicks();
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#guardianPlacementCoefficient()}.
     */
    public double guardianPlacementCoefficient() {
        return RULES.guardianPlacementCoefficient();
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#guardianPlacementRadiusFraction()}.
     */
    public double guardianPlacementRadiusFraction() {
        return RULES.guardianPlacementRadiusFraction();
    }

    /** See {@link BossRules#healGuardian(Mob)}. */
    public void healGuardian(Mob mob) {
        RULES.healGuardian(mob);
    }

    /** See {@link BossRules#guardianDefeatKillsPerTick()}. */
    public int guardianDefeatKillsPerTick() {
        return RULES.guardianDefeatKillsPerTick();
    }

    /** See {@link BossRules#bossBarMinHealth()}. */
    public float bossBarMinHealth() {
        return RULES.bossBarMinHealth();
    }

    /** See {@link BossRules#bossBarLingerTicks()}. */
    public int bossBarLingerTicks() {
        return RULES.bossBarLingerTicks();
    }

    /** See {@link BossRules#killGuardian(Mob, ServerPlayer)}. */
    public void killGuardian(Mob mob, ServerPlayer credited) {
        RULES.killGuardian(mob, credited);
    }

    /** See {@link BossRules#dismiss(Mob)}. */
    public void dismiss(Mob mob) {
        RULES.dismiss(mob);
    }

    /** See {@link BossRules#canBeGuardian(EntityType)}. */
    public boolean canBeGuardian(EntityType<?> type) {
        return RULES.canBeGuardian(type);
    }

    /** See {@link BossRules#guardianTargetScale(int)}. */
    public double guardianTargetScale(int layer) {
        return RULES.guardianTargetScale(layer);
    }

    /** See {@link BossRules#guardianMaxScale()}. */
    public double guardianMaxScale() {
        return RULES.guardianMaxScale();
    }

    /** See {@link BossRules#guardianFitStep()}. */
    public double guardianFitStep() {
        return RULES.guardianFitStep();
    }

    /** See {@link BossRules#guardianAddCost()}. */
    public double guardianAddCost() {
        return RULES.guardianAddCost();
    }

    /** See {@link BossRules#guardianMaxAddsPerSpawn()}. */
    public int guardianMaxAddsPerSpawn() {
        return RULES.guardianMaxAddsPerSpawn();
    }

    /** See {@link BossRules#guardianAddScatterRadius()}. */
    public int guardianAddScatterRadius() {
        return RULES.guardianAddScatterRadius();
    }

    /** See {@link BossRules#trailIntervalTicks()}. */
    public int trailIntervalTicks() {
        return RULES.trailIntervalTicks();
    }

    /** See {@link BossRules#trailLifetimeTicks()}. */
    public int trailLifetimeTicks() {
        return RULES.trailLifetimeTicks();
    }

    /** See {@link BossRules#trailRadius()}. */
    public int trailRadius() {
        return RULES.trailRadius();
    }

    /** See {@link BossRules#equipSunWard(Mob)}. */
    public void equipSunWard(Mob mob) {
        RULES.equipSunWard(mob);
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#applyGuardianStatScaling(Mob, double)}.
     */
    public void applyGuardianStatScaling(Mob mob, double difficultyIntensity) {
        RULES.applyGuardianStatScaling(mob, difficultyIntensity);
    }
}
