package com.arryn.frontiermode.feral.common;

/**
 * Pure, Minecraft-type-free math backing Feral (RM_FRO_038 "Martha"), split out so plain JUnit can
 * test it: this project's test sourceSet has no Minecraft userdev classes on its classpath, the same
 * reason {@code FrontierSicknessLogic} and {@code BorderMathLogic} are separate.
 *
 * <p>Every hostile spawning outside the Frontier is Feral, unconditionally, and density (how many)
 * climbs with distance, plain linear and uncapped (wilds.md#feral: no bucketed tiers, just steadily
 * more). {@link #expectedDensityCount} has the same linear-in-distance shape as
 * {@code FrontierSicknessLogic.severityTarget}, deliberately not
 * {@code wildlifeDensityMultiplier}'s flattening curve. How dangerous a Feral mob is does not follow
 * distance: it is the {@code feral} rung of the stat ladder.
 *
 * <p>The formulas are "safe baseline, replace later" placeholders, not tuned against play.
 */
public final class FeralLogic {

    private FeralLogic() {}

    /**
     * Expected number of extra hostiles spawning alongside one that already spawned
     * {@code frontierDistance} blocks past the Frontier. Density scales with raw distance, not a
     * per-mob conversion chance: plain linear and uncapped, unlike
     * {@code FrontierSicknessLogic.wildlifeDensityMultiplier}'s flattening curve. This is a
     * real-valued <em>expectation</em>, not an integer count -- the caller turns it into an actual
     * spawn count (a whole part spawned unconditionally, a fractional remainder rolled once), the
     * standard way to get an unbiased integer count out of a continuous expected value.
     */
    public static double expectedDensityCount(int frontierDistance, double countScale) {
        if (frontierDistance <= 0) {
            return 0.0;
        }
        return frontierDistance * countScale;
    }
}
