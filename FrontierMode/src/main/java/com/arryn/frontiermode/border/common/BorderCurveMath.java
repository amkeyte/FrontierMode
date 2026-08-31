package com.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.fixture.Shape;

/**
 * Pure, Minecraft-type-free curve evaluation backing
 * {@link BorderMath#intensityAt(com.arryn.frontiermode.border.common.fixture.BorderCurve, double)}
 * -- split out for the same unit-testability reason {@link BorderMathLogic} exists (see its own
 * doc).
 *
 * <p>Per wiki/frontiermode/architecture/border-curve.md's "Open questions", the exact shape
 * functions and what {@code params} holds beyond the shape tag are this node's own Lead Dev call,
 * not settled by the spec -- these three are a starting, clearly-replaceable baseline (same "safe
 * baseline, replace later" framing as {@code DefaultBorderRules.GROWTH_FACTOR}), each mapping
 * {@code normalizedDistance} (0 at the border's center, 1 at its edge) to an intensity in
 * {@code [0, 1]} that climbs as distance to center shrinks, per the worked Guardian Mobs example:
 *
 * <ul>
 *   <li>{@code LINEAR}: a straight-line falloff, {@code 1 - d} (ignores {@code steepness}).</li>
 *   <li>{@code LOG}: a concave falloff via {@code log1p} -- steep near the center, flattening out
 *       toward the edge ("a sharper spike close to the boss, flattening further out", per that
 *       page's worked example), scaled by {@code steepness}.</li>
 *   <li>{@code SQUARE}: a convex falloff, {@code 1 - d^2} (ignores {@code steepness}) -- stays
 *       high longer near the center, then drops off sharply near the edge; the opposite curvature
 *       from {@code LOG}.</li>
 * </ul>
 *
 * <p>{@code normalizedDistance} is clamped into {@code [0, 1]} before evaluation -- a point
 * outside the border ({@code normalizedDistance > 1}) reads as "at the edge," not extrapolated
 * past it.
 */
public final class BorderCurveMath {

    private BorderCurveMath() {}

    public static double intensityAt(Shape shape, double steepness, double normalizedDistance) {
        double d = Math.max(0.0, Math.min(1.0, normalizedDistance));

        double intensity;
        switch (shape) {
            case LINEAR -> intensity = 1.0 - d;
            case LOG -> {
                double k = Math.max(steepness, 1e-6);
                intensity = 1.0 - (Math.log1p(d * k) / Math.log1p(k));
            }
            case SQUARE -> intensity = 1.0 - (d * d);
            default -> throw new IllegalArgumentException("Unhandled Shape: " + shape);
        }

        return Math.max(0.0, Math.min(1.0, intensity));
    }
}
