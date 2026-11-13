package com.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderConstants;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.BorderMathLogic;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.satchel.common.tunable.Tunable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.OptionalInt;

/**
 * How frontier borders are created and how subsequent borders in the "path" are chosen, and Border's balance
 * numbers. Rules propose values only:
 * - they do NOT create or mutate borders
 * - they do NOT access fixtures or bundles
 * - they do NOT enforce authority
 * <p>
 * Returned values are treated as proposals and may be rejected or modified by the authoritative facet.
 * <p>
 * The numbers are an intentionally simple safe baseline:
 * - The initial border is centered at the level spawn position.
 * - Radius is a function of path position alone, growing in waves -- see
 * {@link BorderRules#radiusForPathPosition}.
 * <p>
 * Tunable-backed numbers are constructed fresh at each read.
 */
public final class BorderRules {

    public static final List<String> DEFAULT_BORDER_NAMES = List.of(
            "Ashring",
            "Dawnmark",
            "Ironveil",
            "Stormreach",
            "Emberline",
            "Frostward",
            "Goldfall",
            "Nightwatch",
            "Starbound",
            "Thornwall",
            "Grimline",
            "Skyreach",
            "Redmarch",
            "Shadowmere",
            "Brightward",
            "Deepwatch",
            "Highfall",
            "Runehold",
            "Mistbound",
            "Blackgate"
    );
    // Pregeneration throttle budget (wiki/frontiermode/architecture/border-pregeneration.md) --
    // not tuned against play, the same category as the growth-wave Tunables. A real 1009-chunk
    // (radius 18) disk can't be kept up with at 1 tick/16 chunks or 1 tick/8 chunks: at
    // interval=1 every tick forces fresh, far-out chunk generation back-to-back for 100+
    // consecutive ticks with zero gap to recover in between, so the problem is sustained load,
    // not a per-batch spike -- trimming batch size alone doesn't fix that while it still fires
    // every tick. These values widen the interval too, so there's real breathing room between
    // batches, not just a smaller one.
    private static final int PREGEN_CHUNKS_PER_BATCH = 2;
    private static final long PREGEN_THROTTLE_INTERVAL_TICKS = 4L;

    // The random direction chooseNextCenter picks.
    private static final RandomSource RNG = RandomSource.create();

    private BorderRules() {
    }

    /**
     * Choose the center position for the very first border in a level.
     */
    public static BlockPos chooseInitialCenter(Level level) {
        // Use the shared spawn position as a sensible default.
        return level.getSharedSpawnPos();
    }

    /**
     * Choose the center position for the next border in the path, based
     * on the previously active border.
     */
    public static BlockPos chooseNextCenter(Level level, Border previous) {
        // Move 30 blocks in a random horizontal direction (XZ only)
        double angle = RNG.nextDouble() * Math.PI * 2.0;

        int dx = (int) Math.round(Math.cos(angle) * 30);
        int dz = (int) Math.round(Math.sin(angle) * 30);

        BlockPos c = previous.center();
        return new BlockPos(
                c.getX() + dx,
                c.getY(),
                c.getZ() + dz
        );
    }

    /**
     * The radius of the Border at 1-based position {@code pathPosition} on the path (the first
     * Border is 1). A function of path position alone, never of layer or of a previous Border's
     * radius -- see border.md § Radius: growth in waves.
     *
     * <p>Growth in waves (border.md § Radius: growth in waves). {@code radius(p) = round(base x
     * factor(0) x ... x factor(p-1))}, computed from the base every time so rounding never
     * accumulates and an edit to one Border's radius never shifts the ones after it.
     *
     * <p>Each wave is a half-cosine sweep of {@code borderWaveLength} steps, from
     * {@code 1 + borderWaveAmplitude} down to exactly 1.0 (a flat step, which still recenters), then
     * the next wave opens wide again. Defaults (5, 0.5, 5) give factors 1.5, 1.43, 1.25, 1.07, 1.0 and
     * radii 8, 11, 13, 14, 14, 22, 31, 38, 41, 41, 62 for positions 1-11. The base is the Tunable
     * {@code borderBaseRadius}: a
     * row already in a config CSV wins over this default.
     */
    public static int radiusForPathPosition(Level level, int pathPosition) {
        int base = new Tunable("borderBaseRadius", 1, 1000, 5).intValue();
        float amplitude = new Tunable("borderWaveAmplitude", 0f, 2f, 0.5f).floatValue();
        int waveLength = borderWaveLength();

        long radius = BorderMath.waveRadius(base, amplitude, waveLength, pathPosition);
        return (int) Math.max(BorderConstants.MIN_RADIUS, Math.min(radius, Integer.MAX_VALUE));
    }

    /** Border's growth-wave length (border.md § Radius: growth in waves). */
    public static int borderWaveLength() {
        return new Tunable("borderWaveLength", 2, 20, 5).intValue();
    }

    /**
     * A path position's step within its growth wave, 1 to {@link BorderRules#borderWaveLength()}; the last step
     * is the wave's flat Border. The one place "where in the wave" is computed -- Boss's milestone
     * test and the Diorite Striker read it (border.md § Radius: growth in waves).
     */
    public static int waveStep(int pathPosition) {
        return BorderMathLogic.waveStep(pathPosition, borderWaveLength());
    }

    public static Border getRelevant(List<Border> containing, BlockPos pos) {

        if (containing.isEmpty()) {
            return null;
        }

        Border best = null;
        int bestLayer = Integer.MAX_VALUE;
        double bestCenterDistSq = Double.MAX_VALUE;

        for (Border border : containing) {
            int layer = border.layer();
            double centerDistSq = BorderAPI.MATH.distanceSqToCenter(border, pos);

            if (best == null
                    || layer < bestLayer
                    || (layer == bestLayer && centerDistSq < bestCenterDistSq)) {

                best = border;
                bestLayer = layer;
                bestCenterDistSq = centerDistSq;
            }
        }

        return best;
    }

    @SuppressWarnings("SameReturnValue")
    public static List<String> borderNames() {
        return DEFAULT_BORDER_NAMES;
    }


    // ------------------------------------------------------------------
    // Difficulty (Border Vocabulary: Layer is one input to Difficulty, not
    // the definition of it -- see wiki frontiermode/architecture/difficulty.md).
    // ------------------------------------------------------------------

    /**
     * Converts a Border's Layer into a difficulty rating. The one shared formula both
     * ambient (via {@link BorderRules#ambientDifficultyAt}) and boss difficulty are meant to go through.
     * Identity: a rating equals the layer, and the two words stay distinct.
     */
    public static int layerToDifficulty(int layer) {
        // Layer and Difficulty are vocabulary-distinct even though the rating equals the layer.
        return layer;
    }

    /**
     * Ambient difficulty at a point: resolves Relevance via {@link BorderRules#getRelevant} and, if a
     * Relevant border exists, feeds its layer through {@link BorderRules#layerToDifficulty}. Empty when
     * nothing is Relevant at this point -- mirrors {@link BorderRules#getRelevant}'s own null-for-nothing-
     * contains-this-point contract. Deliberately does not accept a raw layer: composing
     * getRelevant()+layerToDifficulty() is the whole point of this method existing, so callers
     * don't reimplement or skip the Relevance step themselves.
     */
    public static OptionalInt ambientDifficultyAt(List<Border> containing, BlockPos pos) {
        Border relevant = getRelevant(containing, pos);
        if (relevant == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(layerToDifficulty(relevant.layer()));
    }

    // ------------------------------------------------------------------
    // Border Pregeneration (wiki/frontiermode/architecture/border-pregeneration.md).
    // Tuning numbers only, not architecture decisions, and not tuned against play: the same
    // category as the growth-wave Tunables.
    // ------------------------------------------------------------------

    /**
     * Real chunk-generation calls allowed per throttled batch, feeding
     * {@code BorderPregenFixture}'s pacing {@code TickThrottler}.
     */
    public static int pregenChunksPerBatch() {
        return PREGEN_CHUNKS_PER_BATCH;
    }

    /**
     * Minimum ticks between allowed pregeneration batches -- the interval
     * {@code BorderPregenFixture}'s pacing {@code TickThrottler} is built with.
     */
    public static long pregenThrottleIntervalTicks() {
        return PREGEN_THROTTLE_INTERVAL_TICKS;
    }
}
