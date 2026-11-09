package com.arryn.frontiermode.siting.server.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The siting module's one rules helper (siting.md): the shared terrain-suitability check, and every
 * Tunable the starting-site search and the used-ground penalty read. Server-side only.
 */
public interface SitingRules {

    SitingRules ACTIVE = new DefaultSitingRules();

    /** 0 (steepest) to 1 (flat) for a column's immediate footprint -- "safe baseline, replace later". */
    double flatnessScore(Level level, BlockPos ground);

    /**
     * 0 (safe) to 1 (worst) for a column. 1 is disqualifying: fluid at the surface, the world-floor
     * void, or no solid footing. Any other column scores the used-ground penalty, from 0 for a chunk
     * no player has spent time in up to {@link #sitingUsedGroundMaxPenalty()} (below 1) for a
     * well-used one: a rank, never a veto. {@link #footprintSuitable} ignores the penalty.
     */
    double hazardScore(Level level, BlockPos ground);

    /**
     * Footprint mode: pass/fail over a {@code size x size} area centered on {@code center}, every column
     * flat enough and hazard-free, with {@code clearance} blocks of air above each.
     */
    boolean footprintSuitable(Level level, BlockPos center, int size, int clearance);

    /** Logs the first Border's disc must hold, for wood without a trip into the Wilds. */
    int startingSiteMinLogs();

    /** How far below each column's surface the log count looks, to find trunks under leaves. */
    int startingSiteLogDepth();

    /** The share of the disc's columns that may have a fluid as their surface block. */
    double startingSiteMaxWater();

    /** Columns that must have natural stone within {@link #startingSiteStoneDepth()} of the surface. */
    int startingSiteMinStoneColumns();

    int startingSiteStoneDepth();

    /** Distance between rings of candidates, and the farthest ring searched. */
    int startingSiteRingStep();

    int startingSiteSearchLimit();

    /** The real time bound: full checks (each generates a disc) before taking the best one seen. */
    int startingSiteMaxFullChecks();

    /** Ticks players must have spent in a chunk for its used-ground penalty to reach the maximum. */
    int sitingUsedGroundFullTicks();

    /** The most a chunk's used ground adds to a column's hazard score; 0 turns the rule off. */
    double sitingUsedGroundMaxPenalty();
}
