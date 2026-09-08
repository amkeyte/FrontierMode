package com.arryn.frontiermode.border.server.rules;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderConstants;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.OptionalInt;

/**
 * Default, opinionated implementation of {@link BorderRules}.
 * <p>
 * This implementation is intentionally simple:
 * - The initial border is centered at the level spawn position.
 * - The initial radius uses {@link BorderConstants#DEFAULT_RADIUS}.
 * - Subsequent borders reuse the previous center (for now) and grow
 * the radius by a fixed factor, clamped between MIN and MAX.
 * <p>
 * This is meant as a safe baseline and can be replaced by more
 * sophisticated rule sets later.
 */

public final class DefaultBorderRules implements BorderRules {

    public static final int GROWTH_RING_RADIUS = 3;
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
    private static final double GROWTH_FACTOR = 1.5;

    // Pregeneration throttle budget (wiki/frontiermode/architecture/border-pregeneration.md,
    // FRO_092) -- safe baseline, replace later, same category as GROWTH_FACTOR above. Two
    // playtest data points so far, both against a real 1009-chunk (radius 18) disk: 1 tick/16
    // chunks -> "Can't keep up, 45 ticks behind"; 1 tick/8 chunks -> barely better, "41 ticks
    // behind" despite half the batch size. That non-improvement is the tell: at interval=1 every
    // tick was forcing fresh, far-out chunk generation back-to-back for 100+ consecutive ticks
    // with zero gap to recover in between, so the problem is sustained load, not a per-batch
    // spike -- trimming batch size alone doesn't fix that while it still fires every tick. These
    // values widen the interval too, so there's real breathing room between batches, not just a
    // smaller one.
    private static final int PREGEN_CHUNKS_PER_BATCH = 2;
    private static final long PREGEN_THROTTLE_INTERVAL_TICKS = 4L;

    /**
     * future use
     */
    private final RandomSource rng;

    public DefaultBorderRules() {
        this(RandomSource.create());
    }

    public DefaultBorderRules(RandomSource rng) {
        this.rng = rng;
    }

    @Override
    public BlockPos chooseInitialCenter(Level level) {
        // Use the shared spawn position as a sensible default.
        return level.getSharedSpawnPos();
    }

    @Override
    public int chooseInitialRadius(Level level) {
        // Future: could vary based on level type, difficulty, etc.
        return BorderConstants.DEFAULT_RADIUS;
    }

    @Override
    public BlockPos chooseNextCenter(Level level, Border previous) {
        // Move 30 blocks in a random horizontal direction (XZ only)
        double angle = rng.nextDouble() * Math.PI * 2.0;

        int dx = (int) Math.round(Math.cos(angle) * 30);
        int dz = (int) Math.round(Math.sin(angle) * 30);

        BlockPos c = previous.center();
        return new BlockPos(
                c.getX() + dx,
                c.getY(),
                c.getZ() + dz
        );
    }

    @Override
    public int chooseNextRadius(Level level, Border previous) {
        int current = previous.radius();
        int grown = (int) Math.round(current * GROWTH_FACTOR);

        // Clamp using constants to avoid runaway values.
        if (grown < BorderConstants.MIN_RADIUS) {
            grown = BorderConstants.MIN_RADIUS;
        } else if (grown > BorderConstants.MAX_RADIUS) {
            grown = BorderConstants.MAX_RADIUS;
        }

        return grown;
    }

    @Override
    public Border getRelevant(List<Border> containing, BlockPos pos) {

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

    @Override
    public List<String> borderNames() {
        return DEFAULT_BORDER_NAMES;
    }


    // ------------------------------------------------------------------
    // Difficulty
    // ------------------------------------------------------------------

    @Override
    public int layerToDifficulty(int layer) {
        // Safe baseline: identity. Layer and Difficulty are vocabulary-distinct even though this
        // placeholder happens to make them numerically equal -- replace once there's a real curve
        // to tune against (Game Designer/playtest territory, same as GROWTH_FACTOR above).
        return layer;
    }

    @Override
    public OptionalInt ambientDifficultyAt(List<Border> containing, BlockPos pos) {
        Border relevant = getRelevant(containing, pos);
        if (relevant == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(layerToDifficulty(relevant.layer()));
    }

    // ------------------------------------------------------------------
    // Border Pregeneration
    // ------------------------------------------------------------------

    @Override
    public int pregenChunksPerBatch() {
        return PREGEN_CHUNKS_PER_BATCH;
    }

    @Override
    public long pregenThrottleIntervalTicks() {
        return PREGEN_THROTTLE_INTERVAL_TICKS;
    }
}



