package com.arryn.frontiermode.feral.common;

/**
 * Pure, Minecraft-type-free math backing Feral (RM_FRO_038 "Martha") -- split out for the same
 * unit-testability reason {@code FrontierSicknessLogic}/{@code BorderMathLogic} already are: this
 * project's test sourceSet has no Minecraft userdev classes on its classpath.
 *
 * <p>Every hostile spawning outside the Frontier is Feral, unconditionally (design ruling,
 * 2026-09-18 -- superseded the earlier bounded, distance-saturating conversion roll this class
 * used to also hold), and density (how many) now climbs the same uncapped way severity (how
 * dangerous) already does -- per wiki/frontiermode/design/exterior.md#feral's "no bucketed tiers
 * picked from a table, just steadily more twisted and more dangerous the farther out." Both
 * {@link #intensity} and {@link #expectedDensityCount} are the same plain linear-in-distance
 * shape {@code FrontierSicknessLogic.severityTarget} uses -- deliberately not
 * {@code wildlifeDensityMultiplier}'s flattening curve, which is the wrong shape for either one.
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
     * Expected number of extra hostiles spawning alongside one that already spawned
     * {@code frontierDistance} blocks past the Frontier -- design ruling (2026-09-18): density
     * scales with raw distance, not a per-mob conversion chance (that's what {@link #intensity}
     * already covers). Plain linear, uncapped -- same shape as {@link #intensity}, not
     * {@code FrontierSicknessLogic.wildlifeDensityMultiplier}'s flattening one, which this
     * replaces as Feral's own density mechanism. This is a real-valued <em>expectation</em>, not
     * an integer count -- the caller turns it into an actual spawn count (a whole part spawned
     * unconditionally, a fractional remainder rolled once), the standard way to get an unbiased
     * integer count out of a continuous expected value.
     */
    public static double expectedDensityCount(int frontierDistance, double countScale) {
        if (frontierDistance <= 0) {
            return 0.0;
        }
        return frontierDistance * countScale;
    }
}
