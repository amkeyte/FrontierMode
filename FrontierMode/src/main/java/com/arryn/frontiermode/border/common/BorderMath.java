package com.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/**
 * Pure geometry helpers for border containment and distance.
 *
 * All calculations are performed in X/Z space only.
 * Y is intentionally ignored so that math matches rendered borders.
 *
 * This class favors clarity and correctness over micro-optimizations.
 */
public final class BorderMath {

    private BorderMath() {}

    /**
     * Returns true if {@code pos} lies inside or exactly on the border.
     * Uses true Euclidean distance in X/Z space.
     */
    public static boolean isInside(Border border, BlockPos pos) {
        if (border == null || pos == null) {
            return false;
        }

        return isInside(border.radius(), pos, border.center());
    }

    /**
     * Returns true if {@code pos} lies within {@code range} (inclusive),
     * measured in X/Z space from {@code center}.
     *
     * Y is intentionally ignored.
     */
    public static boolean isInside(int range, BlockPos pos, BlockPos center) {
        if (pos == null || center == null) {
            return false;
        }

        double dx = pos.getX() - center.getX();
        double dz = pos.getZ() - center.getZ();

        return (dx * dx + dz * dz) <= (range * range);
    }


    /**
     * Returns the distance from {@code pos} to the border surface.
     *
     * - 0 → inside or on the border
     * - N → N blocks outside the border
     *
     * Distance is computed in X/Z space only.
     */
    public static int distanceToSurface(Border border, BlockPos pos) {
        if (border == null || pos == null) {
            return 0;
        }

        return (int) (distanceSqToCenter(border,pos)-border.radius());
    }

    public static double distanceSqToCenter(Border border, BlockPos pos) {
        double dx = pos.getX() - border.center().getX();
        double dz = pos.getZ() - border.center().getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Uniform-in-disk XZ sample, centered on {@code center}, radius {@code radius} -- the same
     * angle/distance shape {@code DefaultBorderRules.chooseNextCenter()} already uses for its own
     * (much smaller, fixed-30-block) offset, generalized to an arbitrary radius and a real
     * uniform-area distribution rather than a fixed-length hop.
     *
     * <p>Y is intentionally left as {@code center}'s own Y (a placeholder -- see {@link Border}'s
     * XZ-only geometry contract). Callers needing a real ground position (RM_FRO_018's Boss
     * materialization step) resolve Y separately, only once the target chunk is confirmed loaded
     * -- picking a position and resolving its ground height are two different steps for exactly
     * the reason documented on {@code boss.md}'s "Spawn algorithm" section: chunk-loaded state has
     * nothing to do with *where* the column is, only with *when* a block-data-dependent Y can be
     * read.
     *
     * <p>Uses {@code sqrt(rng.nextDouble())} for the radial component so points are uniform across
     * the disk's *area*, not uniform in radius (which would bias samples toward the center) --
     * standard uniform-disk-sampling shape, not something either existing caller needed before
     * since {@code chooseNextCenter()}'s own offset is a fixed length, not an area sample.
     */
    public static BlockPos randomPointInDisk(RandomSource rng, BlockPos center, int radius) {
        if (center == null) {
            throw new IllegalArgumentException("center must not be null");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative: " + radius);
        }

        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(rng.nextDouble()) * radius;

        int dx = (int) Math.round(Math.cos(angle) * dist);
        int dz = (int) Math.round(Math.sin(angle) * dist);

        return new BlockPos(
                center.getX() + dx,
                center.getY(),
                center.getZ() + dz
        );
    }

}
