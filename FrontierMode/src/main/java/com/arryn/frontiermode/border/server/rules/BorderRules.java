package com.arryn.frontiermode.border.server.rules;
/*
 * Strategy interface describing how frontier borders are created and how
 * subsequent borders in the "path" are chosen.
 *
 * Implementations are stateless (or externally configured) and do not
 * directly access fixtures or Satchel. They operate purely on level data
 * and existing {@link Border} instances.
 */

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * BorderRules implementations propose values only.
 *
 * They:
 * - do NOT create or mutate borders
 * - do NOT access fixtures or bundles
 * - do NOT enforce authority
 *
 * Returned values are treated as proposals and may be rejected
 * or modified by the authoritative facet.
 */
public interface BorderRules {

        static final BorderRules ACTIVE = new DefaultBorderRules();

/**
     * Choose the center position for the very first border in a level.
     */

    BlockPos chooseInitialCenter(Level level);


/**
     * Choose the radius for the very first border in a level.
     */

    int chooseInitialRadius(Level level);


/**
     * Choose the center position for the next border in the path, based
     * on the previously active border.
     */

    BlockPos chooseNextCenter(Level level, Border previous);


/**
     * Choose the radius for the next border in the path, based on the
     * previously active border.
     */

    int chooseNextRadius(Level level, Border previous);

    Border getRelevant(List<Border> containing, BlockPos pos);

    List<String> borderNames();

    boolean growPathCriteria(Level level, BlockPos pos, BlockState placed);

    void updateFinderItems(Level level);
}

