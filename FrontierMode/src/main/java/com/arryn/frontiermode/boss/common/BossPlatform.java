package com.arryn.frontiermode.boss.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * A boss's spawn platform: a carved, solid footing under and around it, built once per boss record
 * when its position is final and its chunk is loaded, before its first materialization. See boss.md's
 * "Spawn platform". A flat bedrock pad is a placeholder for a real, themed structure later.
 *
 * <p>Footprint and clearance both scale with the boss's size multiplier (the same multiplier that
 * scales its hitbox). {@link #halfWidth} is the one footprint function -- the Amethyst Compass's
 * bedrock-pad detection reads it too, so two copies of the formula never drift.
 */
public final class BossPlatform {

    // How many blocks of air to clear directly above each bedrock cell at scale 1.0. Prevents mobs
    // from spawning into tree foliage that can sit at ground level.
    private static final int AIR_CLEARANCE = 4;

    private BossPlatform() {
    }

    /**
     * Half-width of the square bedrock platform for a boss of the given size multiplier: the base 3x3
     * (half-width 1) scaled by the multiplier, kept odd-sized so it stays centered -- 3x3 at 1.0, 5x5
     * at 1.5, 7x7 at 2.0.
     */
    public static int halfWidth(float scale) {
        return Math.max(1, Math.round((3.0F * scale - 1.0F) / 2.0F));
    }

    /** Blocks of air cleared above every bedrock cell: {@code max(4, ceil(4 * scale))}. */
    public static int clearance(float scale) {
        return Math.max(AIR_CLEARANCE, (int) Math.ceil(AIR_CLEARANCE * scale));
    }

    /**
     * Places a square bedrock layer one block below {@code bossPos} and clears air above each bedrock
     * cell. {@code bossPos} is the boss's standing position (the air block the mob is at, per
     * {@code MOTION_BLOCKING_NO_LEAVES} heightmap semantics).
     */
    public static void place(Level level, BlockPos bossPos, float scale) {
        int half = halfWidth(scale);
        int clearance = clearance(scale);
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos bedrockCell = bossPos.offset(dx, -1, dz);
                level.setBlock(bedrockCell, Blocks.BEDROCK.defaultBlockState(), 3);
                for (int dy = 0; dy < clearance; dy++) {
                    level.setBlock(bedrockCell.above(dy + 1), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}
