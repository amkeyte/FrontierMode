package com.arryn.frontiermode.feral.common;

/**
 * Pure, Minecraft-type-free math backing Feral (RM_FRO_038 "Martha") -- split out for the same
 * unit-testability reason {@code FrontierSicknessLogic}/{@code BorderMathLogic} already are: this
 * project's test sourceSet has no Minecraft userdev classes on its classpath.
 *
 * <p>Every hostile spawning outside the Frontier is Feral, unconditionally (design ruling,
 * 2026-09-18 -- superseded the earlier bounded, distance-saturating conversion roll this class
 * used to also hold). What's left here scales with distance without a ceiling: {@link #intensity},
 * per wiki/frontiermode/design/exterior.md#feral's "no bucketed tiers picked from a table, just
 * steadily more twisted and more dangerous the farther out" -- the same shape
 * {@code FrontierSicknessLogic.severityTarget} uses for Frontier Sickness's own severity.
 *
 * <p>{@link #densityMultiplier} is the one function here that still saturates rather than
 * climbing without bound -- placeholder pending the distance-scaled mob-count redesign FRO_110's
 * own log tracks, not yet built.
 *
 * <p>Formulas here are explicitly "safe baseline, replace later" placeholders, per the design
 * page's own framing -- nothing here is tuned against real playtest feedback yet.
 */
public final class FeralLogic {

    private FeralLogic() {}

    /**
     * How twisted/dangerous a Feral mob is, as a plain uncapped function of the distance it spawned
     * at -- linear, no designed ceiling. Drives both stat scaling and the tag's displayed number.
     */
    public static double intensity(int frontierDistance, double intensityScale) {
        if (frontierDistance <= 0) {
            return 0.0;
        }
        return frontierDistance * intensityScale;
    }

    /**
     * Density-boost multiplier for a hostile spawning {@code frontierDistance} blocks past the
     * Frontier -- same saturating shape as {@code FrontierSicknessLogic.wildlifeDensityMultiplier}
     * (Sick Wildlife's own precedent), just Feral's own falloff/max constants. {@code 1.0} at the
     * edge itself (no boost), rising toward {@code maxMultiplier} and flattening well before any
     * real distance -- not the uncapped shape {@link #intensity} is.
     */
    public static double densityMultiplier(int frontierDistance, double falloffDistance, double maxMultiplier) {
        if (frontierDistance <= 0 || falloffDistance <= 0.0) {
            return 1.0;
        }
        double rise = 1.0 - Math.exp(-frontierDistance / falloffDistance);
        return 1.0 + (maxMultiplier - 1.0) * rise;
    }
}
