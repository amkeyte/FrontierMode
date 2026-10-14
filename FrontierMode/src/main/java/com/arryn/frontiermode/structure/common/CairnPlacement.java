package com.arryn.frontiermode.structure.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * The arithmetic of where and how many Cairns a boss gets (cairn.md § Data model, § Placement), free
 * of world access so plain JUnit can test it.
 */
public final class CairnPlacement {

    /** A Keeper at this path position and below has no Cairn: {@code floor((p - 15) / 5)} is 0. */
    private static final int COUNT_OFFSET = 15;
    /** One more Cairn on each milestone: every this-many path positions. */
    private static final int COUNT_STEP = 5;

    /** A Cairn sits at least this fraction of the home Border's radius from its boss... */
    private static final double INNER_FRACTION = 0.5;
    /** ...and at most this fraction. */
    private static final double OUTER_FRACTION = 1.0;

    private CairnPlacement() {
    }

    /**
     * How many Cairns a boss at {@code pathPosition} gets: {@code floor((p - 15) / 5)}, none before
     * path position 20, one at 20, two at 25, three at 30, with no cap. A boss with no path position
     * (hand-placed, or attached to an off-path Border) gets none -- its record's own path position, not
     * the layer fallback {@code BossAPI.pathPositionOf} offers.
     */
    public static int count(OptionalInt pathPosition) {
        if (pathPosition.isEmpty()) {
            return 0;
        }
        return Math.max(0, (pathPosition.getAsInt() - COUNT_OFFSET) / COUNT_STEP);
    }

    /** The near edge of the placement band, in blocks from the boss, for a home Border of {@code borderRadius}. */
    public static int innerRadius(int borderRadius) {
        return (int) Math.round(borderRadius * INNER_FRACTION);
    }

    /** The far edge of the placement band, in blocks from the boss. */
    public static int outerRadius(int borderRadius) {
        return (int) Math.round(borderRadius * OUTER_FRACTION);
    }

    /**
     * The chunks a {@code size} x {@code size} footprint centered on {@code center} touches: one to
     * four of them. Cairn loads exactly these before reading the ground (cairn.md § Placement).
     */
    public static List<ChunkPos> footprintChunks(BlockPos center, int size) {
        int half = size / 2;
        int minX = (center.getX() - half) >> 4;
        int maxX = (center.getX() + half) >> 4;
        int minZ = (center.getZ() - half) >> 4;
        int maxZ = (center.getZ() + half) >> 4;

        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }
}
