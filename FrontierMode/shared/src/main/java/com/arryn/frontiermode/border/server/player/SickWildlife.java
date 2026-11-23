package com.arryn.frontiermode.border.server.player;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.FrontierSicknessLogic;
import com.arryn.frontiermode.border.server.rules.PlayerRules;
import com.arryn.frontiermode.core.MobSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;

/**
 * Sick Wildlife's density half: a roll on an already-happening passive spawn in the Wilds, which sometimes
 * brings an extra companion. {@code SpawnCoordinator} calls it on a finalizing spawn, alongside the Sentinel and
 * Feral delegates. Pronounced right at the Frontier edge and flattening to a flat multiplier further out, not a
 * slow climb ({@link FrontierSicknessLogic#wildlifeDensityMultiplier}). The cosmetic tell is
 * {@code WildsTellFixture}'s.
 */
public final class SickWildlife {

    private SickWildlife() {
    }

    // Shared random source for the density roll; static and not persisted, as the Boss and Sentinel ones are.
    private static final RandomSource RNG = RandomSource.create();

    // The companion's spread, and the candidate spots MobSpawns.scatter tries for one.
    private static final int DENSITY_SCATTER_RADIUS = 3;
    private static final int DENSITY_SCATTER_TRIES = 4;

    /**
     * Never cancels the spawn either way: this modifies an already-happening spawn (an extra nearby companion),
     * it is not a summon or a block. A position outside the Frontier, or a mob that is not a passive
     * {@link Animal}, is a silent no-op.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        if (!(mob instanceof Animal)) {
            // Density scaling concerns passive mobs only; hostiles are untouched.
            return;
        }

        BlockPos spawnPos = mob.blockPosition();
        int frontierDistance = BorderAPI.frontierDistanceAt(level, spawnPos);
        if (frontierDistance <= 0) {
            return;
        }

        double multiplier = FrontierSicknessLogic.wildlifeDensityMultiplier(
                frontierDistance, PlayerRules.WILDLIFE_DENSITY_FALLOFF_DISTANCE, PlayerRules.WILDLIFE_DENSITY_MAX_MULTIPLIER);
        double extraSpawnChance = multiplier - 1.0;
        double roll = RNG.nextDouble();

        if (extraSpawnChance <= 0.0 || roll >= extraSpawnChance) {
            return;
        }

        spawnDensityCompanion(mob, level, spawnPos);
    }

    /**
     * Spawns one extra copy of {@code mob}'s own type near {@code nearPos}: an approximation of a spawn-rate
     * multiplier, since the finalize-spawn event fires per already-decided attempt and does not control how many
     * attempts vanilla makes. {@code VANILLA} setup: vanilla's {@code Mob#finalizeSpawn} runs but the Forge event
     * does not, so this never re-enters {@link #onMobSpawnFinalize} for the companion it creates.
     */
    private static void spawnDensityCompanion(Mob mob, ServerLevel level, BlockPos nearPos) {
        BlockPos spot = MobSpawns.scatter(level, mob, nearPos, DENSITY_SCATTER_RADIUS, 1.0F, DENSITY_SCATTER_TRIES);
        MobSpawns.spawn(level, mob.getType(), spot, MobSpawns.Setup.VANILLA);
    }
}
