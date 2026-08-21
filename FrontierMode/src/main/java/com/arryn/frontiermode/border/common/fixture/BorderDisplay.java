package com.arryn.frontiermode.border.common.fixture;

import net.minecraft.core.BlockPos;

/**
 * Human-readable formatting helpers for Border feedback.
 * Intentionally presentation-only.
 */
public final class BorderDisplay {

    private BorderDisplay() {}

    /**
     * @param pathIndex this border's position in the canonical path order (0-based), or -1 if the
     *                   border is off-path. Rendered as {@code LEV-<pathIndex>}, or {@code LEV--}
     *                   when off-path -- distinct from {@code LAY}, which is {@link Border#layer()}
     *                   and is, per RM_FRO_015, definitionally unrelated to path position.
     */
    public static String fullInfo(Border b, int pathIndex) {
        BlockPos c = b.center();

        return b.displayName()
                + " | id=" + b.id()
                + " | C: " + c.getX() + ", " + c.getY() + ", " + c.getZ() + ")"
                + " | R:" + b.radius()
                + " | " + levLay(pathIndex, b.layer());
    }

    /**
     * @param pathIndex see {@link #fullInfo(Border, int)}.
     */
    public static String shortInfo(Border b, int pathIndex) {
        BlockPos c = b.center();

        return b.displayName()
                + " | C: " + c.getX() + ", " + c.getY() + ", " + c.getZ() + ")"
                + " | R:" + b.radius()
                + " | " + levLay(pathIndex, b.layer());
    }

    private static String levLay(int pathIndex, int layer) {
        String lev = pathIndex < 0 ? "LEV--" : "LEV-" + pathIndex;
        return lev + "|LAY-" + layer;
    }
}
