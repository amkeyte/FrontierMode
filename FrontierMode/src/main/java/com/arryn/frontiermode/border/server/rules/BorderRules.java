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
 * <p>
 * They:
 * - do NOT create or mutate borders
 * - do NOT access fixtures or bundles
 * - do NOT enforce authority
 * <p>
 * Returned values are treated as proposals and may be rejected
 * or modified by the authoritative facet.
 */
public interface BorderRules {

        BorderRules ACTIVE = new DefaultBorderRules();

/**
     * Choose the center position for the very first border in a level.
     */

    BlockPos chooseInitialCenter(Level level);


/**
     * Choose the center position for the next border in the path, based
     * on the previously active border.
     */

    BlockPos chooseNextCenter(Level level, Border previous);


/**
     * The radius of the Border at 1-based position {@code pathPosition} on the path (the first
     * Border is 1). A function of path position alone, never of layer or of a previous Border's
     * radius -- see border.md § Radius: growth in waves.
     */

    int radiusForPathPosition(Level level, int pathPosition);

    /** Border's growth-wave length (border.md § Radius: growth in waves). */
    int borderWaveLength();

    /**
     * A path position's step within its growth wave, 1 to {@link #borderWaveLength()}; the last step
     * is the wave's flat Border. The one place "where in the wave" is computed -- Boss's milestone
     * test and the Diorite Striker read it (border.md § Radius: growth in waves).
     */
    int waveStep(int pathPosition);

    Border getRelevant(List<Border> containing, BlockPos pos);

    @SuppressWarnings("SameReturnValue")
    List<String> borderNames();

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

    // ------------------------------------------------------------------
    // Border Pregeneration (wiki/frontiermode/architecture/border-pregeneration.md, FRO_092).
    // Tuning numbers only, not architecture decisions -- same "safe baseline, replace later"
    // category as the growth-wave Tunables. Kept on this interface rather than a separate
    // PregenRules sibling, matching this interface's existing one-rules-helper-per-module
    // precedent (see the Difficulty section above for the same reasoning).
    // ------------------------------------------------------------------

    /**
     * Real chunk-generation calls allowed per throttled batch, feeding
     * {@code BorderPregenFixture}'s pacing {@code TickThrottler}.
     */
    int pregenChunksPerBatch();

    /**
     * Minimum ticks between allowed pregeneration batches -- the interval
     * {@code BorderPregenFixture}'s pacing {@code TickThrottler} is built with.
     */
    long pregenThrottleIntervalTicks();
}

