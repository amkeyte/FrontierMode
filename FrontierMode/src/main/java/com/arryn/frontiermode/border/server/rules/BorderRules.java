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

import java.util.List;
import java.util.OptionalInt;

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

    void updateFinderItems(Level level);

    // ------------------------------------------------------------------
    // Difficulty (Border Vocabulary: Layer is one input to Difficulty, not
    // the definition of it -- see wiki frontiermode/architecture/difficulty.md).
    // Kept on this interface rather than a separate DifficultyRules sibling:
    // one rules helper per module, per project owner's call.
    // ------------------------------------------------------------------

    /**
     * Converts a Border's Layer into a difficulty rating. The one shared formula both
     * ambient (via {@link #ambientDifficultyAt}) and boss difficulty are meant to go through --
     * safe-baseline placeholder for now, not a locked curve.
     */
    int layerToDifficulty(int layer);

    /**
     * Ambient difficulty at a point: resolves Relevance via {@link #getRelevant} and, if a
     * Relevant border exists, feeds its layer through {@link #layerToDifficulty}. Empty when
     * nothing is Relevant at this point -- mirrors {@link #getRelevant}'s own null-for-nothing-
     * contains-this-point contract. Deliberately does not accept a raw layer: composing
     * getRelevant()+layerToDifficulty() is the whole point of this method existing, so callers
     * don't reimplement or skip the Relevance step themselves.
     */
    OptionalInt ambientDifficultyAt(List<Border> containing, BlockPos pos);
}

