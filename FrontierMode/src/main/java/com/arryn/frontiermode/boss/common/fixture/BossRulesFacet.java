package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.server.rules.BossRules;
import com.arryn.frontiermode.boss.server.rules.DefaultBossRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

    private final BossFixture fixture;

    BossRulesFacet(BossFixture fixture) {
        this.fixture = fixture;
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

    /** See {@link com.arryn.frontiermode.boss.server.rules.BossRules#glowEnabled()}. */
    public boolean glowEnabled() {
        return RULES.glowEnabled();
    }

    /** See {@link com.arryn.frontiermode.boss.server.rules.BossRules#setGlowEnabled(boolean)}. */
    public void setGlowEnabled(boolean value) {
        RULES.setGlowEnabled(value);
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

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#guardianTier(double)}.
     */
    public int guardianTier(double difficultyIntensity) {
        return RULES.guardianTier(difficultyIntensity);
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#tagGuardian(Mob, int, double, ServerLevel)}.
     */
    public void tagGuardian(Mob mob, int tier, double difficultyIntensity, ServerLevel level) {
        RULES.tagGuardian(mob, tier, difficultyIntensity, level);
    }

    /**
     * See {@link com.arryn.frontiermode.boss.server.rules.BossRules#applyGuardianStatScaling(Mob, double)}.
     */
    public void applyGuardianStatScaling(Mob mob, double difficultyIntensity) {
        RULES.applyGuardianStatScaling(mob, difficultyIntensity);
    }
}
