package com.arryn.frontiermode.feral.common;

/**
 * Pure, Minecraft-type-free math backing Feral (RM_FRO_038 "Martha") -- split out for the same
 * unit-testability reason {@code FrontierSicknessLogic}/{@code BorderMathLogic} already are: this
 * project's test sourceSet has no Minecraft userdev classes on its classpath.
 *
 * <p>Two independent functions of the same raw frontierDistance, per
 * wiki/frontiermode/design/exterior.md#feral: "no bucketed tiers picked from a table, just
 * steadily more twisted and more dangerous the farther out." A roll <em>chance</em> has to live in
 * {@code [0,1]} regardless (a probability can't be uncapped no matter what it's driven by), but
 * once a spawn hits, its <em>intensity</em> is deliberately uncapped -- the same shape
 * {@code FrontierSicknessLogic.severityTarget} uses for Frontier Sickness's own severity, not
 * {@code wildlifeDensityMultiplier}'s flattening curve.
 *
 * <p>Both formulas here are explicitly "safe baseline, replace later" placeholders, per that
 * design page's own framing -- nothing here is tuned against real playtest feedback yet.
 */
public final class FeralLogic {

    private FeralLogic() {}

    /**
     * Chance a hostile spawning {@code frontierDistance} blocks past the Frontier becomes Feral --
     * {@code 0.0} at the edge itself, rising toward {@code maxChance} (never past it, since this
     * backs an actual probability roll) per {@code falloffDistance}, the distance at which it's
     * already ~63% of the way there. {@code frontierDistance <= 0} always returns {@code 0.0}.
     */
    public static double spawnChance(int frontierDistance, double falloffDistance, double maxChance) {
        if (frontierDistance <= 0 || falloffDistance <= 0.0) {
            return 0.0;
        }
        double rise = 1.0 - Math.exp(-frontierDistance / falloffDistance);
        return maxChance * rise;
    }

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
