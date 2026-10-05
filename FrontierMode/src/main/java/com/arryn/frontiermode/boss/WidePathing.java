package com.arryn.frontiermode.boss;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * FRO_224: a scaled Sentinel or Keeper wider than 0.75 blocks stood still at its first path node,
 * forever. {@code PathNavigation#followThePath} (Forge's MC-94054 fix) tests the mob against
 * {@code node + (width + 1) / 2} with a reach of {@code width / 2}, so a mob standing at a block's
 * centre -- where it spawns, and where the path's own move target ({@code node + (int) (width + 1) * 0.5})
 * sends it -- sits exactly on the edge of the reach, and float rounding decides it is just outside.
 * It never advances, has no distance left to walk, never moves (speed 0), and vanilla's stuck check
 * cannot fire at speed 0. Falling advances the path on a separate branch, which is why breaking the
 * block under one freed it.
 *
 * <p>{@link #advanceIfOnWaypoint} advances the path by one node when the mob is on the very
 * waypoint the move controller is walking it to (the entity position {@code Path} itself computes),
 * within the same reach vanilla uses. Narrow mobs are left to vanilla.
 */
final class WidePathing {

    // Vanilla's own threshold for the reach formula: wider than this, reach is half the width.
    private static final float WIDE = 0.75F;

    private WidePathing() {
    }

    static void advanceIfOnWaypoint(Mob mob) {
        if (mob.getBbWidth() <= WIDE || !mob.onGround()) {
            return;
        }
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return;
        }
        Vec3 waypoint = path.getNextEntityPos(mob);
        double reach = mob.getBbWidth() / 2.0;
        if (Math.abs(mob.getX() - waypoint.x) <= reach
                && Math.abs(mob.getZ() - waypoint.z) <= reach
                && Math.abs(mob.getY() - waypoint.y) < 1.0) {
            path.advance();
        }
    }
}
