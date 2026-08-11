package com.arryn.frontiermode.border.common.fixture;

import net.minecraft.core.BlockPos;

/**
 * Human-readable formatting helpers for Border feedback.
 * Intentionally presentation-only.
 */
public final class BorderDisplay {

    private BorderDisplay() {}

    public static String fullInfo(Border b) {
        BlockPos c = b.center();

        return b.displayName()
                + " | id=" + b.id()
                + " | C: " + c.getX() + ", " + c.getY() + ", " + c.getZ() + ")"
                + " | R:" + b.radius()
                + " | L:" + b.layerIndex();
    }

    public  static String shortInfo(Border b){
        BlockPos c = b.center();

        return b.displayName()
                + " | C: " + c.getX() + ", " + c.getY() + ", " + c.getZ() + ")"
                + " | R:" + b.radius()
                + " | L:" + b.layerIndex();
    }
}
