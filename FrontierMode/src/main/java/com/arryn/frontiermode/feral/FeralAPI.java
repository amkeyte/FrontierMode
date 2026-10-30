package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;

/**
 * The feral module's public surface (feral.md § Class map): the two entry points
 * {@code coordination.SpawnCoordinator} calls on a spawn, in a fixed order -- Boss, Border and Feral all
 * need a turn on the same spawn, and the Slime deny comes first. Feral depends on Border, never the
 * reverse.
 */
public final class FeralAPI {

    private FeralAPI() {
    }

    /**
     * Wilds policy: Slime and MagmaCube (a {@code Slime}) are removed from the Wilds only --
     * where {@code frontierDistanceAt > 0} -- so Feral rules govern out there, and they spawn at the
     * ordinary vanilla rate inside the border. The caller denies the spawn with
     * {@code MobSpawnFinalizing#denySpawn()}, not a cancel, which only skips
     * {@code Mob#finalizeSpawn}'s initialization and still lets the entity spawn.
     */
    public static boolean shouldDenySpawn(Mob mob, ServerLevel level) {
        boolean deny = mob instanceof Slime && BorderAPI.frontierDistanceAt(level, mob.blockPosition()) > 0;
        if (deny) {
            OUT.debug("[Feral] Wilds spawn of " + EntityType.getKey(mob.getType()) + " at "
                    + mob.blockPosition().toShortString() + " is denied");
        }
        return deny;
    }

    /**
     * The density-boost roll only; it tags nothing ({@link FeralTagging#onHostileJoining} is the tagging
     * authority). Currently a no-op, the density mechanic being switched off ({@link FeralDensity}). Never
     * cancels the spawn either way.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        FeralDensity.rollOnSpawn(mob, level);
    }
}
