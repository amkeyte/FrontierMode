package com.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.core.BlockPos;

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

}
