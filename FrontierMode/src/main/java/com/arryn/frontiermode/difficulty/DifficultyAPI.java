package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.difficulty.common.LadderMath;
import com.arryn.frontiermode.difficulty.server.rules.DifficultyRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.piglin.Piglin;

import java.util.OptionalInt;

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

    /**
     * An ally: an iron golem (village-spawned or player-built) or a tamed wolf. Allies take the
     * ordinary growth (difficulty.md § Allies), never attack a Keeper (boss.md), and are what Sentinels
     * attack first (sentinels.md § Defending the boss).
     */
    public static boolean isAlly(Entity entity) {
        return entity instanceof IronGolem || (entity instanceof Wolf wolf && wolf.isTame());
    }

    /**
     * Whether a mob takes the ally rung as it joins the level: an {@link #isAlly ally}, or any wolf, wild
     * or tame (difficulty.md § Allies). Wider than {@code isAlly} on purpose -- {@code isAlly} drives
     * targeting, and a wild wolf is not an ally to the player -- so a wolf is on the ladder from the
     * moment it exists and taming changes nothing about its rung.
     */
    public static boolean isLadderAlly(Entity entity) {
        return isAlly(entity) || entity instanceof Wolf;
    }

    /**
     * Puts a Feral mob -- fresh in the Wilds, where there is no Relevant Border and so no ordinary rung --
     * on the {@code feral} rung (difficulty.md § The stat ladder): {@code 1 + growth x (n - 1)} with
     * {@code n} the path tip's position, the same lookup the Nether uses ({@link #pathTipPosition}). Where
     * the mob stands does not matter. The numbers live in the {@code frontiermode:ladder} MobData
     * namespace, so the one damage handler scales its hits and the first rung to reach a mob still wins.
     * {@code n} is recorded as the rung's step, so the Info report shows the level. The outcome says why
     * nothing was written: {@link RungOutcome#ALREADY_RUNG} when a rung already had the mob, or
     * {@link RungOutcome#NO_PATH_TIP} when there is no path tip yet, in which case the mob stays vanilla.
     */
    public static RungOutcome applyFeralRung(Mob mob) {
        OptionalInt tip = pathTipPosition(mob.getServer());
        if (tip.isEmpty() || tip.getAsInt() < 1) {
            return RungOutcome.NO_PATH_TIP;
        }
        int n = tip.getAsInt();
        DifficultyRules rules = DifficultyRules.ACTIVE;
        boolean applied = Ladder.apply(mob, Ladder.RUNG_FERAL, n,
                LadderMath.multiplier(rules.feralHealthGrowth(), n),
                LadderMath.multiplier(rules.feralDamageGrowth(), n));
        return applied ? RungOutcome.APPLIED : RungOutcome.ALREADY_RUNG;
    }

    /**
     * The Overworld path tip's 1-based position, "how far the player has come" -- the {@code n} the
     * Nether's ordinary rung and the {@code feral} rung share. The Overworld has the only path, so this
     * is the world's tip, not a per-player value. Empty with no path or before the Overworld's Border
     * facet is ready.
     */
    public static OptionalInt pathTipPosition(MinecraftServer server) {
        return BorderAPI.PATH(server.overworld())
                .map(path -> OptionalInt.of(path.size()))
                .orElseGet(OptionalInt::empty);
    }

    /** The mob's place on the stat ladder as one Info report line (level, rung, multipliers), if it has one. */
    public static java.util.Optional<String> describe(Mob mob) {
        return Ladder.describe(mob);
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
    public static boolean applyKeeperRung(Mob mob, int keeperPathPosition, double healthMultiplier,
                                          double damageMultiplier) {
        return Ladder.apply(mob, Ladder.RUNG_KEEPER, keeperPathPosition, healthMultiplier, damageMultiplier);
    }

    /**
     * Puts {@code mob} on the Sentinel rung (difficulty.md § One formula, a rung for each kind of mob):
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
        return Ladder.apply(mob, Ladder.RUNG_SENTINEL, keeperPathPosition,
                LadderMath.multiplier(healthGrowth, keeperPathPosition),
                LadderMath.multiplier(damageGrowth, keeperPathPosition));
    }
}
