package com.arryn.frontiermode.siting;

import com.arryn.frontiermode.siting.common.SitingMath;
import com.arryn.frontiermode.siting.server.rules.SitingRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The siting module's public surface (siting.md): is this ground good, and where to look. Server-side
 * only. Boss, structure (Cairn) and the starting-site search depend on it; it depends on Border for
 * geometry and never the reverse.
 */
public final class SitingAPI {

    /** The one shared clearance, in blocks: a Keeper's standing room and a Cairn's 3-tall pillar plus a spare. */
    public static final int FOOTPRINT_CLEARANCE = 5;

    private SitingAPI() {
    }

    /** 0 (steepest) to 1 (flat) for {@code ground}'s immediate footprint. */
    public static double flatnessScore(Level level, BlockPos ground) {
        return SitingRules.ACTIVE.flatnessScore(level, ground);
    }

    /** 0 (safe) to 1 (worst, disqualifying) for {@code ground}. */
    public static double hazardScore(Level level, BlockPos ground) {
        return SitingRules.ACTIVE.hazardScore(level, ground);
    }

    /** Footprint mode: pass/fail over a {@code size x size} area, with {@code clearance} blocks of air above. */
    public static boolean footprintSuitable(Level level, BlockPos center, int size, int clearance) {
        return SitingRules.ACTIVE.footprintSuitable(level, center, size, clearance);
    }

    /** Eight points per ring at {@code step}, {@code 2 x step}, ... out to {@code limit}, nearest ring first. */
    public static List<BlockPos> ringCandidates(BlockPos origin, int step, int limit) {
        return SitingMath.ringCandidates(origin, step, limit);
    }
}
