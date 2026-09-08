package com.arryn.frontiermode.border.common;

/**
 * Pure, Minecraft-type-free math backing Frontier Sickness's severity climb
 * (RM_FRO_037 "Brenda," FRO_099) and Sick Wildlife's spawn-density multiplier -- split out for
 * the same unit-testability reason {@link BorderMathLogic}/{@link BorderCurveMath} already are:
 * this project's test sourceSet has no Minecraft userdev classes on its classpath.
 *
 * <p>Both formulas here are explicitly "safe baseline, replace later" placeholders, per
 * wiki/frontiermode/design/exterior.md's own framing ("Lead Dev owns picking sane initial numbers
 * to playtest against") -- FRO_099's own scope note makes the same call for this ticket
 * specifically. Nothing here is tuned against real playtest feedback yet.
 */
public final class FrontierSicknessLogic {

    private FrontierSicknessLogic() {}

    // ------------------------------------------------------------------
    // Severity: a target-and-catch-up climb, per
    // wiki/frontiermode/design/exterior.md#frontier-sickness -- "current distance sets how bad
    // it's trying to get, and the player's actual condition climbs toward that target the longer
    // they stay, rather than snapping straight to a fixed value."
    // ------------------------------------------------------------------

    /**
     * The severity target for a given raw Frontier-distance -- uncapped, per that page's own "no
     * designed ceiling" requirement. Placeholder baseline: linear in distance, scaled by
     * {@code targetScale} (see {@code PlayerRules.frontierSicknessTargetScale()}).
     */
    public static double severityTarget(int frontierDistance, double targetScale) {
        if (frontierDistance <= 0) {
            return 0.0;
        }
        return frontierDistance * targetScale;
    }

    /**
     * One tick's worth of catch-up toward {@code target} from {@code previousSeverity} --
     * exponential approach, {@code climbRate} in {@code (0, 1]} (the fraction of the remaining
     * gap closed per tick). {@code climbRate <= 0} would never move; {@code climbRate >= 1} would
     * snap instantly -- neither honors the design's "climbs toward that target the longer they
     * stay, rather than snapping straight to a fixed value," so callers are expected to keep it
     * strictly between the two.
     *
     * <p>Symmetric in both directions -- this same formula carries severity back down toward 0
     * once {@code target} is 0 again (player back inside the Frontier). Never negative: floored
     * at 0 rather than letting floating-point overshoot on the final tick of a decay produce a
     * small negative severity.
     */
    public static double climbSeverity(double previousSeverity, double target, double climbRate) {
        double next = previousSeverity + (target - previousSeverity) * climbRate;
        return Math.max(0.0, next);
    }

    // ------------------------------------------------------------------
    // Sick Wildlife's density multiplier -- per project owner direction (FRO_099 planning): NOT
    // a continuous climb like severity above. Pronounced right at the Frontier edge (a fast
    // initial rise), flattening to a flat multiplier further out -- not an unbounded climb the
    // way severity/Feral's own scaling is.
    // ------------------------------------------------------------------

    /**
     * Spawn-density multiplier for a passive mob spawning {@code frontierDistance} blocks past
     * the Frontier -- {@code 1.0} at the edge itself, rising quickly (per {@code falloffDistance},
     * the distance at which it's already ~63% of the way to {@code maxMultiplier}) and flattening
     * out at {@code maxMultiplier} well before any real distance -- deliberately not a slow,
     * continuous climb the way {@link #severityTarget} is. {@code frontierDistance <= 0} (not in
     * the Exterior at all) always returns {@code 1.0} -- no boost.
     */
    public static double wildlifeDensityMultiplier(int frontierDistance, double falloffDistance, double maxMultiplier) {
        if (frontierDistance <= 0 || falloffDistance <= 0.0) {
            return 1.0;
        }
        double rise = 1.0 - Math.exp(-frontierDistance / falloffDistance);
        return 1.0 + (maxMultiplier - 1.0) * rise;
    }
}
