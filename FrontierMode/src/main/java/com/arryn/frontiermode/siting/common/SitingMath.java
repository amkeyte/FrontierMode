package com.arryn.frontiermode.siting.common;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The siting module's arithmetic that needs no world (siting.md), so plain JUnit can test it: where to
 * look, and how a candidate that failed the starting-site check is scored.
 */
public final class SitingMath {

    private SitingMath() {
    }

    // The four compass directions and the four diagonals, as unit steps.
    private static final int[][] DIRECTIONS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    /**
     * Eight points per ring -- north, east, south, west and the four diagonals -- at {@code step},
     * {@code 2 x step}, ... out to {@code limit}, nearest ring first. Each point is {@code k x step}
     * blocks from {@code origin} (Euclidean, so a diagonal's offsets are the ring distance over root
     * two); the Y of each is {@code origin}'s. A ring search stays close to its origin and can't march
     * off in one direction into an ocean.
     */
    public static List<BlockPos> ringCandidates(BlockPos origin, int step, int limit) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int ring = 1; (long) ring * step <= limit; ring++) {
            double distance = (double) ring * step;
            for (int[] d : DIRECTIONS) {
                // A diagonal's unit step has length root two; scale it back to the ring distance.
                double scale = (d[0] != 0 && d[1] != 0) ? distance / Math.sqrt(2.0) : distance;
                candidates.add(origin.offset((int) Math.round(d[0] * scale), 0, (int) Math.round(d[1] * scale)));
            }
        }
        return candidates;
    }

    /** What the full check measured over a candidate's disc. */
    public record Reading(int logs, double waterFraction, int stoneColumns, boolean keeperSpot) {
    }

    /** The four thresholds a disc has to meet. */
    public record Requirements(int minLogs, double maxWater, int minStoneColumns) {
    }

    /** Whether a candidate passed, and how close it came (0-4: one point per requirement, partial credit). */
    public record Verdict(boolean passes, double score) {
    }

    /**
     * Judges one disc: wood, mostly land, stone without tools, and room for the first Keeper
     * (siting.md § Judging a candidate). Passes only if all four hold. A candidate that fails is still
     * scored -- each requirement gives 1 when met and partial credit by how close it came -- so the
     * best of a failed search can be kept.
     */
    public static Verdict judge(Reading reading, Requirements need) {
        double wood = need.minLogs() <= 0 ? 1.0 : Math.min(1.0, reading.logs() / (double) need.minLogs());
        double land;
        if (reading.waterFraction() <= need.maxWater()) {
            land = 1.0;
        } else {
            // Over the limit: credit falls as the water share climbs past it.
            land = need.maxWater() <= 0.0 ? 0.0 : need.maxWater() / reading.waterFraction();
        }
        double stone = need.minStoneColumns() <= 0
                ? 1.0
                : Math.min(1.0, reading.stoneColumns() / (double) need.minStoneColumns());
        double room = reading.keeperSpot() ? 1.0 : 0.0;

        boolean passes = wood >= 1.0 && land >= 1.0 && stone >= 1.0 && room >= 1.0;
        return new Verdict(passes, wood + land + stone + room);
    }
}
