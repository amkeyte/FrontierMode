package com.arryn.frontiermode.siting.server.rules;

import com.arryn.satchel.common.tunable.Tunable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Safe-baseline siting numbers (siting.md). The terrain checks are moved here unchanged in behavior from
 * {@code DefaultBossRules}; the Tunables are constructed fresh at each read.
 */
public final class DefaultSitingRules implements SitingRules {

    // flatnessScore()'s own safe baseline: candidate height compared against four points this far
    // out in each cardinal direction. A tuning number, not an architecture decision.
    private static final int FLATNESS_SAMPLE_DISTANCE = 4;

    // flatnessScore(): a height delta at or above this many blocks scores 0 (steepest); below it
    // scales linearly toward 1 (flattest). A tuning number.
    private static final double FLATNESS_MAX_DELTA = 8.0;

    // Footprint mode's flatness floor: where to draw a boolean pass/fail line on the continuous
    // flatness score is this implementation's call, same as every other threshold here.
    private static final double FOOTPRINT_MIN_FLATNESS = 0.5;

    @Override
    public double flatnessScore(Level level, BlockPos candidate) {
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

    @Override
    public double hazardScore(Level level, BlockPos candidate) {
        // 1.0 (worst, disqualifying) for any of three real hazards --
        //   - the resolved position itself sits in a fluid (open water/lava);
        //   - the column bottoms out at/near the world floor (a genuine "nothing found" void
        //     column reads back at the heightmap's own floor sentinel);
        //   - the block directly underfoot isn't actually solid. A ravine or cave mouth
        //     breaching the surface can pull MOTION_BLOCKING_NO_LEAVES's own result down into
        //     open space well above the world floor, which the two checks above can't see.
        // 0.0 otherwise. Not a locked algorithm.
        if (!level.getBlockState(candidate).getFluidState().isEmpty()) {
            return 1.0;
        }
        if (candidate.getY() <= level.getMinBuildHeight() + 1) {
            return 1.0;
        }
        BlockPos below = candidate.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return 1.0;
        }
        return 0.0;
    }

    @Override
    public boolean footprintSuitable(Level level, BlockPos center, int size, int clearance) {
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos column = center.offset(dx, 0, dz);
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

                if (hazardScore(level, ground) >= 1.0) {
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

    @Override
    public int startingSiteMinLogs() {
        return new Tunable("startingSiteMinLogs", 0, 100, 5).intValue();
    }

    @Override
    public int startingSiteLogDepth() {
        return new Tunable("startingSiteLogDepth", 1, 32, 12).intValue();
    }

    @Override
    public double startingSiteMaxWater() {
        return new Tunable("startingSiteMaxWater", 0f, 1f, 0.3f).floatValue();
    }

    @Override
    public int startingSiteMinStoneColumns() {
        return new Tunable("startingSiteMinStoneColumns", 0, 500, 10).intValue();
    }

    @Override
    public int startingSiteStoneDepth() {
        return new Tunable("startingSiteStoneDepth", 0, 16, 4).intValue();
    }

    @Override
    public int startingSiteRingStep() {
        return new Tunable("startingSiteRingStep", 16, 1000, 100).intValue();
    }

    @Override
    public int startingSiteSearchLimit() {
        return new Tunable("startingSiteSearchLimit", 100, 10000, 1500).intValue();
    }

    @Override
    public int startingSiteMaxFullChecks() {
        return new Tunable("startingSiteMaxFullChecks", 1, 200, 24).intValue();
    }
}
