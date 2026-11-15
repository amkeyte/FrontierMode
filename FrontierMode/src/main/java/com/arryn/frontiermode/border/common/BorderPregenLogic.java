package com.arryn.frontiermode.border.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure, Minecraft-type-free chunk-offset enumeration backing
 * {@code BorderPregenFixture}'s spiral-out generation order -- split out for the same
 * unit-testability reason {@link BorderMathLogic} exists (see its own doc). Offsets are relative
 * chunk-grid coordinates {@code {dcx, dcz}} from a border's own center chunk, ordered by
 * ascending distance from center ("spiraling out from center", per
 * wiki/frontiermode/architecture/border-pregeneration.md's "Persisted state stays small" section)
 * so a throttled, resumable pass covers the disk from the middle outward.
 *
 * <p>Deterministic order is load-bearing, not cosmetic: a persisted {@code cursor} is only a
 * valid resume point across a restart if the same {@code chunkRadius} always produces the exact
 * same sequence.
 */
public final class BorderPregenLogic {

    private static final Map<Integer, List<int[]>> OFFSETS_BY_RADIUS = new ConcurrentHashMap<>();

    private BorderPregenLogic() {}

    /**
     * Every chunk offset within {@code chunkRadius} chunks of the center (inclusive), ordered by
     * ascending squared distance from center, ties broken by {@code dcx} then {@code dcz} for a
     * fully deterministic order.
     */
    public static List<int[]> chunkOffsetsInDisk(int chunkRadius) {
        if (chunkRadius < 0) {
            throw new IllegalArgumentException("chunkRadius must not be negative: " + chunkRadius);
        }

        List<int[]> offsets = new ArrayList<>();
        long radiusSq = (long) chunkRadius * chunkRadius;

        for (int dcx = -chunkRadius; dcx <= chunkRadius; dcx++) {
            for (int dcz = -chunkRadius; dcz <= chunkRadius; dcz++) {
                long distSq = (long) dcx * dcx + (long) dcz * dcz;
                if (distSq <= radiusSq) {
                    offsets.add(new int[] {dcx, dcz});
                }
            }
        }

        offsets.sort(
                Comparator.<int[]>comparingLong(o -> (long) o[0] * o[0] + (long) o[1] * o[1])
                        .thenComparingInt(o -> o[0])
                        .thenComparingInt(o -> o[1])
        );

        return offsets;
    }

    /**
     * Whether chunk {@code (chunkX, chunkZ)} lies inside the disk of {@code chunkRadius} chunks around
     * {@code (centerChunkX, centerChunkZ)}, boundary included. The same {@code dx² + dz² <= r²} rule
     * {@link #chunkOffsetsInDisk} enumerates by, so a chunk is inside exactly when its offset from the
     * center is in that list.
     */
    public static boolean insideDisk(int chunkX, int chunkZ, int centerChunkX, int centerChunkZ, int chunkRadius) {
        if (chunkRadius < 0) {
            throw new IllegalArgumentException("chunkRadius must not be negative: " + chunkRadius);
        }

        long dx = (long) chunkX - centerChunkX;
        long dz = (long) chunkZ - centerChunkZ;
        return dx * dx + dz * dz <= (long) chunkRadius * chunkRadius;
    }

    /**
     * {@link #chunkOffsetsInDisk} for a radius, built once and reused: the list is deterministic and a
     * pregeneration pass asks for it every batch. The returned list is unmodifiable and shared; its
     * {@code int[]} entries must not be written.
     */
    public static List<int[]> offsetsFor(int chunkRadius) {
        return OFFSETS_BY_RADIUS.computeIfAbsent(chunkRadius, r -> List.copyOf(chunkOffsetsInDisk(r)));
    }

    /**
     * Converts a border's radius in blocks to a chunk radius, rounding up -- any block radius
     * that reaches partway into a chunk still needs that whole chunk generated.
     */
    public static int chunkRadiusFor(int blockRadius) {
        if (blockRadius < 0) {
            throw new IllegalArgumentException("blockRadius must not be negative: " + blockRadius);
        }

        return (blockRadius + 15) / 16;
    }
}
