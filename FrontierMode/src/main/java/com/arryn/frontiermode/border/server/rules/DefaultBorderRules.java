package com.arryn.frontiermode.border.server.rules;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderConstants;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.satchel.common.tunable.Tunable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.OptionalInt;

/**
 * Default, opinionated implementation of {@link BorderRules}.
 * <p>
 * This implementation is intentionally simple:
 * - The initial border is centered at the level spawn position.
 * - Radius is a function of path position alone, growing in waves -- see
 * {@link #radiusForPathPosition}.
 * <p>
 * This is meant as a safe baseline and can be replaced by more
 * sophisticated rule sets later.
 */

public final class DefaultBorderRules implements BorderRules {

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
    // Pregeneration throttle budget (wiki/frontiermode/architecture/border-pregeneration.md,
    // FRO_092) -- safe baseline, replace later, same category as the growth-wave Tunables. Two
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

    /**
     * Growth in waves (border.md § Radius: growth in waves; FRO_190). {@code radius(p) = round(base x
     * factor(0) x ... x factor(p-1))}, computed from the base every time so rounding never
     * accumulates and an edit to one Border's radius never shifts the ones after it.
     *
     * <p>Each wave is a half-cosine sweep of {@code borderWaveLength} steps, from
     * {@code 1 + borderWaveAmplitude} down to exactly 1.0 (a flat step, which still recenters), then
     * the next wave opens wide again. Defaults (20, 0.5, 5) give factors 1.5, 1.43, 1.25, 1.07, 1.0 and
     * radii 30, 43, 54, 57, 57, 86, 123, 154, 165, 165, 247 for positions 1-11.
     */
    @Override
    public int radiusForPathPosition(Level level, int pathPosition) {
        int base = new Tunable("borderBaseRadius", 1, 1000, 20).intValue();
        float amplitude = new Tunable("borderWaveAmplitude", 0f, 2f, 0.5f).floatValue();
        int waveLength = new Tunable("borderWaveLength", 2, 20, 5).intValue();

        double radius = base;
        for (int step = 0; step < Math.max(pathPosition, 1); step++) {
            radius *= waveFactor(step % waveLength, waveLength, amplitude);
        }
        return Math.max(BorderConstants.MIN_RADIUS, (int) Math.round(radius));
    }

    private static double waveFactor(int stepInWave, int waveLength, double amplitude) {
        double sweep = (1.0 + Math.cos(Math.PI * stepInWave / (waveLength - 1))) / 2.0;
        return 1.0 + amplitude * sweep;
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
        // to tune against (Game Designer/playtest territory).
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



