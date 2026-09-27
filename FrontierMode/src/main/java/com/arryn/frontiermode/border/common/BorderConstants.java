package com.arryn.frontiermode.border.common;

/**
 * Central location for numeric constants related to border gameplay.
 * <p>
 * Placeholder values for now — fill out as design matures.
 */
public final class BorderConstants {

    private BorderConstants() {}

    // No maximum: growth continues indefinitely (border.md § Radius: growth in waves).
    public static final int MIN_RADIUS = 1;

    // FRO_059: a UI-sane bound, not a gameplay number -- 32 comfortably covers the longest
    // existing default name in DefaultBorderRules.DEFAULT_BORDER_NAMES ("Shadowmere") with
    // headroom. See border.md's "Proposal identity and validation" section.
    public static final int MAX_DISPLAY_NAME_LENGTH = 32;

    // Add growth factors, decay rates, etc. here later
}
