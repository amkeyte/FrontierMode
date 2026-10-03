package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.difficulty.common.LadderMath;
import com.arryn.frontiermode.difficulty.server.rules.DifficultyRules;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.piglin.Piglin;

/**
 * The difficulty module's public surface (difficulty.md § Module shape): what Boss calls to put a
 * mob on a rung of the stat ladder, and the hostile test. Boss depends on Difficulty, never the
 * reverse.
 */
public final class DifficultyAPI {

    private DifficultyAPI() {
    }

    /**
     * The mobs vanilla removes on Peaceful -- the design's definition of "hostile" -- with two
     * adjustments (difficulty.md § Which mobs): a {@code Monster} except the Piglin, plus the Ghast,
     * Phantom, and Slime and Magma Cube. The Warden is in (it is a {@code Monster}); the Wither is
     * out. {@code Mob#shouldDespawnInPeaceful} is protected, so this mirrors it.
     */
    public static boolean isLadderHostile(Mob mob) {
        if (mob instanceof WitherBoss) {
            return false;
        }
        if (mob instanceof Monster) {
            return !(mob instanceof Piglin);
        }
        // Slime also covers MagmaCube.
        return mob instanceof Ghast || mob instanceof Phantom || mob instanceof Slime;
    }

    /** The Keeper's health growth: the ordinary growth plus the Keeper's (boss.md § Keeper stats). */
    public static double keeperHealthGrowthTotal() {
        DifficultyRules rules = DifficultyRules.ACTIVE;
        return rules.ordinaryHealthGrowth() + rules.keeperHealthGrowth();
    }

    /** The Keeper's damage growth: the ordinary growth plus the Keeper's. */
    public static double keeperDamageGrowthTotal() {
        DifficultyRules rules = DifficultyRules.ACTIVE;
        return rules.ordinaryDamageGrowth() + rules.keeperDamageGrowth();
    }

    /**
     * Puts {@code mob} -- a Keeper, before it joins the level -- on the Keeper rung with the given
     * multipliers, which Boss works out from its stat tables (boss.md § Keeper stats). {@code false}
     * if a rung already had it.
     */
    public static boolean applyKeeperRung(Mob mob, double healthMultiplier, double damageMultiplier) {
        return Ladder.apply(mob, Ladder.RUNG_KEEPER, healthMultiplier, damageMultiplier);
    }

    /**
     * Puts {@code mob} on the Sentinel rung (difficulty.md § One formula, three rungs):
     * {@code n} is its Keeper's path position, and the growth is the ordinary growth plus a Sentinel
     * share rising with {@code difficultyIntensity}, 1 beside the boss. Called before the mob joins
     * the level, so the ordinary rung never touches it. {@code false} if a rung already had it.
     */
    public static boolean applySentinelRung(Mob mob, int keeperPathPosition, double difficultyIntensity) {
        DifficultyRules rules = DifficultyRules.ACTIVE;
        double healthGrowth = LadderMath.sentinelGrowth(rules.ordinaryHealthGrowth(),
                rules.sentinelHealthGrowthFar(), rules.sentinelHealthGrowthNear(), difficultyIntensity);
        double damageGrowth = LadderMath.sentinelGrowth(rules.ordinaryDamageGrowth(),
                rules.sentinelDamageGrowthFar(), rules.sentinelDamageGrowthNear(), difficultyIntensity);
        return Ladder.apply(mob, Ladder.RUNG_SENTINEL,
                LadderMath.multiplier(healthGrowth, keeperPathPosition),
                LadderMath.multiplier(damageGrowth, keeperPathPosition));
    }
}
