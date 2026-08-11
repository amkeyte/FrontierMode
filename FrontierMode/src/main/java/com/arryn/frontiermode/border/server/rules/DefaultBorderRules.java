package com.arryn.frontiermode.border.server.rules;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderConstants;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.server.rules.items.BorderPathCompass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

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
            int layer = border.layerIndex();
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

        var tip = BorderAPI.borders(level)
                .flatMap(b -> b.PATH.tip())
                .orElseThrow(); //no path tip


        return BorderMath.isInside(GROWTH_RING_RADIUS + 1, pos, tip.center());
    }

    @Override
    public void updateFinderItems(Level level) {

        var tip = BorderAPI.borders(level)
                .flatMap(b -> b.PATH.tip())
                .orElseThrow(); //no path tip

        BlockPos tipCenter = tip.center();

        for (Player player : level.players()) {
            BorderPathCompass.giveOrUpdate(player, tipCenter);
        }
    }

}



