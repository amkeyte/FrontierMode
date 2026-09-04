package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.server.rules.BossRules;
import com.arryn.frontiermode.boss.server.rules.DefaultBossRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

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

    /**
     * See {@link BossRules#choosePosition(ServerLevel, Border)}.
     */
    public BlockPos choosePosition(ServerLevel level, Border border) {
        return RULES.choosePosition(level, border);
    }

    /**
     * See {@link BossRules#flatnessScore(ServerLevel, BlockPos)}.
     */
    public double flatnessScore(ServerLevel level, BlockPos candidate) {
        return RULES.flatnessScore(level, candidate);
    }

    /**
     * See {@link BossRules#hazardScore(ServerLevel, BlockPos)}.
     */
    public double hazardScore(ServerLevel level, BlockPos candidate) {
        return RULES.hazardScore(level, candidate);
    }

    /**
     * See {@link BossRules#materialize(ServerLevel, BlockPos, int)}.
     */
    public Optional<Mob> materialize(ServerLevel level, BlockPos position, int layer) {
        return RULES.materialize(level, position, layer);
    }
}
