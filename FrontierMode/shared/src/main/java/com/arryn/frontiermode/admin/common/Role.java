package com.arryn.frontiermode.admin.common;

/**
 * What a marked mob is there for in the debug view, and its marker colour (ARGB, fully opaque). A common
 * class, so the common-typed {@link DebugViewTick} can hold it without naming a client type.
 */
public enum Role {
    BOSS(0xFFFFAA00, true),
    SENTINEL(0xFFFF55FF, false),
    FERAL(0xFF55FF55, false);

    private final int color;
    private final boolean tagged;

    Role(int color, boolean tagged) {
        this.color = color;
        this.tagged = tagged;
    }

    public int color() {
        return color;
    }

    /** Whether this role also shows its name tag in the view (a sentinel carries none). */
    public boolean tagged() {
        return tagged;
    }
}
