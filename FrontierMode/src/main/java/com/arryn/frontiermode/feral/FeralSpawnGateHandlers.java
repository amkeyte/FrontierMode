package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

/**
 * Feral's two natural-spawn gates: hostiles in the Wilds spawn regardless of light level. Vanilla
 * refuses a bright spawn at two separate points -- the placement check before the mob exists, and
 * the position check after it is created and placed -- and both apply a light test to a
 * {@code Monster}, so both are lifted. See feral.md § How Feral hooks the game; the same two-gate
 * shape as sentinels in daylight ({@code SentinelJigHandlers}).
 *
 * <p>Bare, unscoped signals, installed from {@link FeralModule}'s level jig; no jig-key guard.
 */
final class FeralSpawnGateHandlers {

    private FeralSpawnGateHandlers() {
    }

    /**
     * Gate 1, placement. Acts only for {@link MobCategory#MONSTER} types at a position in the Wilds;
     * everywhere else the result is left unset and vanilla's own checks are unchanged.
     *
     * <ul>
     *   <li><b>The nearest player is in the Wilds too:</b> {@code deny()} -- around a player out
     *       there, the pack is the population, not natural spawns.
     *   <li><b>Otherwise</b> (the nearest player hasn't crossed, or none is found):
     *       {@code allow()}, so the Wilds reads as dangerous from inside the Frontier. {@code allow()}
     *       bypasses vanilla's whole monster predicate, not just its darkness test, so the spawn
     *       surface is re-checked first; if it fails, the result stays unset and vanilla refuses
     *       (FRO_170).
     * </ul>
     *
     * <p>Not a real {@link ServerLevel} (e.g. a {@code WorldGenRegion} during chunk generation):
     * left unset.
     */
    static void onSpawnPlacementCheck(SpawnPlacementCheckSignal signal) {
        if (signal.entityType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        //noinspection resource
        if (!(signal.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = signal.pos();
        if (BorderAPI.frontierDistanceAt(level, pos) <= 0) {
            return;
        }

        Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), -1.0, false);
        if (nearest != null && BorderAPI.frontierDistanceAt(level, nearest.blockPosition()) > 0) {
            signal.deny();
            return;
        }

        @SuppressWarnings("unchecked")
        EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) signal.entityType();
        if (MobSpawns.surfaceAllows(level, mobType, signal.spawnType(), pos)) {
            signal.allow();
        }
    }

    /**
     * Gate 2, position. After the mob is created and placed, vanilla checks
     * {@code checkSpawnRules} (a light test for every {@code Monster}) and
     * {@code checkSpawnObstruction}. A {@link MobCategory#MONSTER} standing in the Wilds -- for a natural
     * spawn, one gate 1 allowed -- is allowed if nothing is in its way; {@code allow()}
     * bypasses the obstruction test too, so it's re-checked here (FRO_170).
     */
    static void onSpawnPositionCheck(SpawnPositionCheckSignal signal) {
        Mob mob = signal.mob();
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        //noinspection resource
        if (!(signal.level() instanceof ServerLevel level)) {
            return;
        }
        if (BorderAPI.frontierDistanceAt(level, mob.blockPosition()) <= 0) {
            return;
        }
        if (MobSpawns.obstructionClear(level, mob)) {
            signal.allow();
        }
    }
}
