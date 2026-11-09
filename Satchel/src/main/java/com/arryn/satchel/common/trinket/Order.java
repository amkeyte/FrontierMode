package com.arryn.satchel.common.trinket;

/**
 * The named anchors for a Trinket's {@code order}: an int, lower runs first, default {@link #NORMAL}.
 * A Trinket may declare any int; the anchors leave room on both sides, so a Trinket can sit just
 * before or after one, as in {@code Order.EARLY - 1} or {@code Order.NORMAL + 10}.
 *
 * <p>
 * The order sorts across every jig, scope and module in the foundation's one global Trinket list;
 * see {@code TrinketTick}.
 */
public final class Order {

    private Order() {
    }

    /** Runs before the bulk of module logic -- state other Trinkets read this tick. */
    public static final int EARLY = -1000;

    /** The default. */
    public static final int NORMAL = 0;

    /** Runs after the bulk of module logic -- readers of what other Trinkets wrote this tick. */
    public static final int LATE = 1000;
}
