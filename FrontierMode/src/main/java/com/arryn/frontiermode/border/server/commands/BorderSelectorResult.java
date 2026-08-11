package com.arryn.frontiermode.border.server.commands;

import net.minecraft.core.BlockPos;

/**
 * Parsed representation of a border selector.
 */
public final class BorderSelectorResult {

    public enum Mode {
        RELEVANT,
        INDEX,
        INSIDE,
        COORD,
        ALL,
        NONE
    }

    public final Mode mode;
    public final int index;
    public final BlockPos coord;

    private BorderSelectorResult(Mode mode, int index, BlockPos coord) {
        this.mode = mode;
        this.index = index;
        this.coord = coord;
    }

    public static BorderSelectorResult none() {
        return new BorderSelectorResult(Mode.NONE, -1, null);
    }

    public static BorderSelectorResult relevant() {
        return new BorderSelectorResult(Mode.RELEVANT, -1, null);
    }

    public static BorderSelectorResult index(int idx) {
        return new BorderSelectorResult(Mode.INDEX, idx, null);
    }

    public static BorderSelectorResult inside() {
        return new BorderSelectorResult(Mode.INSIDE, -1, null);
    }

    public static BorderSelectorResult coord(BlockPos pos) {
        return new BorderSelectorResult(Mode.COORD, -1, pos);
    }

    public static BorderSelectorResult all() {
        return new BorderSelectorResult(Mode.ALL, -1, null);
    }
}
