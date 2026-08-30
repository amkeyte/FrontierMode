package com.arryn.frontiermode.boss.server.commands;

/**
 * Parsed representation of a boss selector -- FRO_057's four modes, sized down from the full
 * design space on wiki/frontiermode/architecture/boss-commands.md's "Selector scheme" (no
 * {@code @id}/{@code @name}/{@code @border} this pass; mirrors
 * {@code BorderSelectorResult}'s shape).
 */
public final class BossSelectorResult {

    public enum Mode {
        POSITION,
        NEAREST,
        ALL,
        NONE
    }

    public final Mode mode;
    public final int index;

    private BossSelectorResult(Mode mode, int index) {
        this.mode = mode;
        this.index = index;
    }

    public static BossSelectorResult none() {
        return new BossSelectorResult(Mode.NONE, -1);
    }

    public static BossSelectorResult nearest() {
        return new BossSelectorResult(Mode.NEAREST, -1);
    }

    public static BossSelectorResult position(int index) {
        return new BossSelectorResult(Mode.POSITION, index);
    }

    public static BossSelectorResult all() {
        return new BossSelectorResult(Mode.ALL, -1);
    }
}
