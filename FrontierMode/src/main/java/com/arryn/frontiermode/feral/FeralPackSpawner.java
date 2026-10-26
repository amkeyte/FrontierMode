package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.feral.server.rules.FeralRules;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * The pack's geometry (feral.md § The pack): which Feral mobs are ahead of a player, in the same cone
 * pack members spawn in. A helper keeps no field; it takes the player and presence as arguments.
 */
final class FeralPackSpawner {

    private FeralPackSpawner() {
    }

    /**
     * How many of {@code feral} are inside {@code area} and ahead of the player. Counting ahead only is
     * the point: a pack the player has run past would otherwise keep satisfying the quota and block a
     * fresh one in front.
     */
    static int countAhead(Collection<Mob> feral, AABB area, Vec3 playerPos, float playerYaw) {
        int count = 0;
        for (Mob mob : feral) {
            if (area.intersects(mob.getBoundingBox()) && isAheadOfPlayer(mob, playerPos, playerYaw)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether {@code mob} is inside the cone {@link FeralRules#packSpawnConeHalfAngleDegrees()} either
     * side of the player's own yaw -- the same cone pack members spawn in, so a mob has to be in roughly
     * the direction a pack would be placed to count as part of it, not just technically in front.
     *
     * <p>Yaw, not {@code getLookAngle()}: a pure horizontal heading, immune to the pitch-driven shrinking
     * a 3D look vector's {@code x}/{@code z} components would have when the player looks down.
     */
    static boolean isAheadOfPlayer(Mob mob, Vec3 playerPos, float playerYaw) {
        Vec3 toMob = mob.position().subtract(playerPos);
        if (toMob.x == 0.0 && toMob.z == 0.0) {
            return true;
        }

        double toMobAngle = Math.atan2(-toMob.x, toMob.z);
        double yawRad = Math.toRadians(playerYaw);
        double diff = normalizeAngleRad(toMobAngle - yawRad);
        return Math.abs(diff) <= Math.toRadians(FeralRules.packSpawnConeHalfAngleDegrees());
    }

    /** Wraps a radian angle into {@code (-PI, PI]}, so comparing angles across the +/-180 seam works. */
    private static double normalizeAngleRad(double angle) {
        double twoPi = Math.PI * 2.0;
        angle = angle % twoPi;
        if (angle > Math.PI) {
            angle -= twoPi;
        } else if (angle < -Math.PI) {
            angle += twoPi;
        }
        return angle;
    }
}
