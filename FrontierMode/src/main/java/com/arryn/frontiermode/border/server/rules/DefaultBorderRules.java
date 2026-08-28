package com.arryn.frontiermode.border.server.rules;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderConstants;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.server.rules.items.BorderPathCompass;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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
            double centerDistSq = BorderMath.distanceSqToCenter(border, pos);

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

    @Override
    public boolean growPathCriteria(Level level, BlockPos pos, BlockState placed) {
        if (!placed.is(Blocks.GOLD_BLOCK)) return false;

        var tipOpt = BorderAPI.PATH(level)
                .flatMap(BordersPathFacet::tip);

        // No path tip yet is a real, expected state -- a fresh world/fixture before any border
        // has ever been grown -- not an error. BordersPathFacet.grow() already treats this case
        // as first-class (its own empty-path branch, which ignores `pos` and is always
        // spawn-centered per DefaultBorderRules' own class doc above). So here: any gold
        // block placement is valid to trigger that initial growth when there's nothing to be
        // "close enough" to yet. Was `.orElseThrow()` -- crashed the server on the very first
        // gold block ever placed in a fresh world. See SAT_023-adjacent finding, FRO_015.
        if (tipOpt.isEmpty()) return true;

        return BorderMath.isInside(GROWTH_RING_RADIUS + 1, pos, tipOpt.get().center());
    }

    @Override
    public void updateFinderItems(Level level) {

        var tipOpt = BorderAPI.PATH(level)
                .flatMap(BordersPathFacet::tip);

        // Same "no path tip yet" state as growPathCriteria above -- nothing to point players at
        // until a border exists, not an error. Was `.orElseThrow()`, which crashed the server
        // tick (via SatchelEventBus.post -> ScopeEvent.Tick) on every fresh-world tick before the
        // first border was ever grown. See FRO_015.
        if (tipOpt.isEmpty()) return;

        BlockPos tipCenter = tipOpt.get().center();

        for (Player player : level.players()) {
            BorderPathCompass.giveOrUpdate(player, tipCenter);
        }
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
}



