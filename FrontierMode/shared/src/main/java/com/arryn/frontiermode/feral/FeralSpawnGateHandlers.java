package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.core.MobSpawns;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Feral's two natural-spawn gates: hostiles in the Wilds spawn regardless of light level. Vanilla
 * refuses a bright spawn at two separate points -- the placement check before the mob exists, and
 * the position check after it is created and placed -- and both apply a light test to a
 * {@code Monster}, so both are lifted. See feral.md § How Feral hooks the game; the same two-gate
 * shape as sentinels in daylight ({@code SentinelJigHandlers}).
 *
 * <p>The consumers of {@link WildsPlacementCheck} and {@link WildsPositionCheck}. The ingress's
 * resolvers have already bounced every firing outside the Wilds and every non-hostile type, so a consumer
 * only decides.
 */
final class FeralSpawnGateHandlers {

    private FeralSpawnGateHandlers() {
    }

    /**
     * Gate 1, placement. The event is only built for a {@code MobCategory.MONSTER} type at a position in
     * the Wilds.
     *
     * <ul>
     *   <li><b>The nearest player is in the Wilds too:</b> {@code deny()} -- around a player out
     *       there, the pack is the population, not natural spawns.
     *   <li><b>Otherwise</b> (the nearest player hasn't crossed, or none is found):
     *       {@code allow()}, so the Wilds reads as dangerous from inside the Frontier. {@code allow()}
     *       bypasses vanilla's whole monster predicate, not just its darkness test, so the spawn
     *       surface is re-checked first; if it fails, the result stays unset and vanilla refuses
     *.
     * </ul>
     */
    static void onPlacementCheck(WildsPlacementCheck event) {
        Level level = event.level();
        BlockPos pos = event.pos();

        Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), -1.0, false);
        if (nearest != null && BorderAPI.frontierDistanceAt(level, nearest.blockPosition()) > 0) {
            event.deny();
            OUT.debug("[Feral] placement gate denied " + EntityType.getKey(event.entityType()) + " at "
                    + pos.toShortString() + ": a player is out in the Wilds");
            return;
        }

        @SuppressWarnings("unchecked")
        EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) event.entityType();
        if (MobSpawns.surfaceAllows(level, mobType, event.spawnType(), pos)) {
            event.allow();
            OUT.debug("[Feral] placement gate allowed " + EntityType.getKey(mobType) + " at " + pos.toShortString());
        }
    }

    /**
     * Gate 2, position. After the mob is created and placed, vanilla checks
     * {@code checkSpawnRules} (a light test for every {@code Monster}) and
     * {@code checkSpawnObstruction}. The event is only built for a {@code MobCategory.MONSTER} standing in
     * the Wilds -- for a natural spawn, one gate 1 allowed -- and it is allowed if nothing is in its way;
     * {@code allow()} bypasses the obstruction test too, so it's re-checked here.
     */
    static void onPositionCheck(WildsPositionCheck event) {
        Mob mob = event.mob();
        if (MobSpawns.obstructionClear(event.level(), mob)) {
            event.allow();
            OUT.debug("[Feral] position gate allowed " + EntityType.getKey(mob.getType()) + " at "
                    + mob.blockPosition().toShortString());
        }
    }
}
