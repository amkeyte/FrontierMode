package com.arryn.frontiermode.siting.common;

import com.arryn.frontiermode.siting.common.SitingMath;
import com.arryn.satchel.common.tunable.Tunable;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The siting module's one rules helper (siting.md): the shared terrain-suitability check, the used-ground penalty
 * on {@code hazardScore}, and every Tunable the starting-site search and the penalty read. Safe-baseline numbers,
 * Tunable-backed and constructed fresh at each read. Server-side only.
 */
public final class SitingRules {

    // flatnessScore()'s own safe baseline: candidate height compared against four points this far
    // out in each cardinal direction. A tuning number, not an architecture decision.
    private static final int FLATNESS_SAMPLE_DISTANCE = 4;

    // flatnessScore(): a height delta at or above this many blocks scores 0 (steepest); below it
    // scales linearly toward 1 (flattest). A tuning number.
    private static final double FLATNESS_MAX_DELTA = 8.0;

    // Footprint mode's flatness floor: where to draw a boolean pass/fail line on the continuous
    // flatness score is this implementation's call, same as every other threshold here.
    private static final double FOOTPRINT_MIN_FLATNESS = 0.5;

    // Whether the first above-zero used-ground penalty has been logged. A static, so it logs once per launch,
    // not once per world.
    private static boolean usedGroundLogged;

    private SitingRules() {
    }

    /** 0 (steepest) to 1 (flat) for a column's immediate footprint. A baseline, not tuned against play. */
    public static double flatnessScore(Level level, BlockPos candidate) {
        // Safe baseline: compares candidate's own resolved height against its four cardinal
        // neighbors, FLATNESS_SAMPLE_DISTANCE blocks over -- a flat plateau scores near 1.0, a
        // sharp cliff edge scores near 0.0. Not a locked algorithm.
        int centerY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate).getY();

        int maxDelta = 0;
        int[][] offsets = {
                {FLATNESS_SAMPLE_DISTANCE, 0}, {-FLATNESS_SAMPLE_DISTANCE, 0},
                {0, FLATNESS_SAMPLE_DISTANCE}, {0, -FLATNESS_SAMPLE_DISTANCE}
        };
        for (int[] offset : offsets) {
            BlockPos neighbor = new BlockPos(
                    candidate.getX() + offset[0], candidate.getY(), candidate.getZ() + offset[1]);
            int neighborY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, neighbor).getY();
            maxDelta = Math.max(maxDelta, Math.abs(neighborY - centerY));
        }

        return Math.max(0.0, 1.0 - (maxDelta / FLATNESS_MAX_DELTA));
    }

    /**
     * 0 (safe) to 1 (worst) for a column. 1 is disqualifying: fluid at the surface, the world-floor
     * void, or no solid footing. Any other column scores the used-ground penalty, from 0 for a chunk
     * no player has spent time in up to {@link SitingRules#sitingUsedGroundMaxPenalty()} (below 1) for a
     * well-used one: a rank, never a veto. {@link SitingRules#footprintSuitable} ignores the penalty.
     */
    public static double hazardScore(Level level, BlockPos candidate) {
        // 1.0 (worst, disqualifying) for a disqualifying hazard, otherwise the used-ground penalty,
        // which the Tunable ranges keep below 1. Not a locked algorithm.
        if (disqualified(level, candidate)) {
            return 1.0;
        }
        return usedGroundPenalty(level, candidate);
    }

    /**
     * Whether the column has any of three real hazards --
     *   - the resolved position itself sits in a fluid (open water/lava);
     *   - the column bottoms out at/near the world floor (a genuine "nothing found" void
     *     column reads back at the heightmap's own floor sentinel);
     *   - the block directly underfoot isn't actually solid. A ravine or cave mouth
     *     breaching the surface can pull MOTION_BLOCKING_NO_LEAVES's own result down into
     *     open space well above the world floor, which the two checks above can't see.
     * The footprint check asks this directly, so it never pays for the used-ground penalty.
     */
    private static boolean disqualified(Level level, BlockPos candidate) {
        if (!level.getBlockState(candidate).getFluidState().isEmpty()) {
            return true;
        }
        if (candidate.getY() <= level.getMinBuildHeight() + 1) {
            return true;
        }
        BlockPos below = candidate.below();
        return !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** The penalty for the chunk's inhabited time (siting.md § Used ground ranks lower); 0 with the rule off. */
    private static double usedGroundPenalty(Level level, BlockPos candidate) {
        double maxPenalty = sitingUsedGroundMaxPenalty();
        if (maxPenalty <= 0.0) {
            return 0.0;
        }
        long inhabitedTime = level.getChunkAt(candidate).getInhabitedTime();
        int fullTicks = sitingUsedGroundFullTicks();
        double penalty = SitingMath.usedGroundPenalty(inhabitedTime, fullTicks, maxPenalty);
        if (penalty > 0.0 && !usedGroundLogged) {
            usedGroundLogged = true;
            OUT.debug("[Siting] hazardScore(): used-ground penalty above 0 for the first time, " + inhabitedTime
                    + " inhabited ticks at " + candidate.getX() + ", " + candidate.getZ() + " scores " + penalty
                    + " (full at " + fullTicks + " ticks, max " + maxPenalty + ")");
        }
        return penalty;
    }

    /**
     * Footprint mode: pass/fail over a {@code size x size} area centered on {@code center}, every column
     * flat enough and hazard-free, with {@code clearance} blocks of air above each.
     */
    public static boolean footprintSuitable(Level level, BlockPos center, int size, int clearance) {
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos column = center.offset(dx, 0, dz);
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

                if (disqualified(level, ground)) {
                    return false;
                }
                if (flatnessScore(level, ground) < FOOTPRINT_MIN_FLATNESS) {
                    return false;
                }
                for (int dy = 1; dy <= clearance; dy++) {
                    if (!level.getBlockState(ground.above(dy)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Logs the first Border's disc must hold, for wood without a trip into the Wilds. */
    public static int startingSiteMinLogs() {
        return new Tunable("startingSiteMinLogs", 0, 100, 5).intValue();
    }

    /** How far below each column's surface the log count looks, to find trunks under leaves. */
    public static int startingSiteLogDepth() {
        return new Tunable("startingSiteLogDepth", 1, 32, 12).intValue();
    }

    /** The share of the disc's columns that may have a fluid as their surface block. */
    public static double startingSiteMaxWater() {
        return new Tunable("startingSiteMaxWater", 0f, 1f, 0.3f).floatValue();
    }

    /** Columns that must have natural stone within {@link SitingRules#startingSiteStoneDepth()} of the surface. */
    public static int startingSiteMinStoneColumns() {
        return new Tunable("startingSiteMinStoneColumns", 0, 500, 10).intValue();
    }

    public static int startingSiteStoneDepth() {
        return new Tunable("startingSiteStoneDepth", 0, 16, 4).intValue();
    }

    /** Distance between rings of candidates, and the farthest ring searched. */
    public static int startingSiteRingStep() {
        return new Tunable("startingSiteRingStep", 16, 1000, 100).intValue();
    }

    public static int startingSiteSearchLimit() {
        return new Tunable("startingSiteSearchLimit", 100, 10000, 1500).intValue();
    }

    /** The real time bound: full checks (each generates a disc) before taking the best one seen. */
    public static int startingSiteMaxFullChecks() {
        return new Tunable("startingSiteMaxFullChecks", 1, 200, 24).intValue();
    }

    /** Ticks players must have spent in a chunk for its used-ground penalty to reach the maximum. */
    public static int sitingUsedGroundFullTicks() {
        return new Tunable("sitingUsedGroundFullTicks", 1, 3600000, 12000).intValue();
    }

    /** The most a chunk's used ground adds to a column's hazard score; 0 turns the rule off. */
    public static double sitingUsedGroundMaxPenalty() {
        return new Tunable("sitingUsedGroundMaxPenalty", 0f, 0.9f, 0.5f).floatValue();
    }
}
