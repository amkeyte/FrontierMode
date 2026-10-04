package com.arryn.frontiermode.diorite.common;

/**
 * The Diorite Striker's color arithmetic (diorite-tools.md § What a strike reads), free of Minecraft
 * types so plain JUnit can test it. The color is Difficulty against the path tip's, blended blue
 * through yellow to red.
 */
public final class StrikerMath {

    // RGB, 0-1.
    private static final float[] BLUE = {0.20F, 0.40F, 1.00F};
    private static final float[] YELLOW = {1.00F, 0.90F, 0.20F};
    private static final float[] RED = {1.00F, 0.10F, 0.10F};

    private StrikerMath() {
    }

    /**
     * Where a patch of ground sits between blue (0) and red (1): {@code clamp((d - 1) / (dTip - 1), 0, 1)}.
     * While the tip's Difficulty is 1 or less every strike is blue. Relative by design: a pocket that
     * strikes red early strikes blue once the frontier has moved on.
     */
    public static double heat(int difficulty, int tipDifficulty) {
        if (tipDifficulty <= 1) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (difficulty - 1.0) / (tipDifficulty - 1.0)));
    }

    /** The spark color for {@code heat}: blue at 0, yellow at 0.5, red at 1, blended in between. */
    public static float[] color(double heat) {
        double t = Math.max(0.0, Math.min(1.0, heat));
        return t <= 0.5 ? lerp(BLUE, YELLOW, t * 2.0) : lerp(YELLOW, RED, (t - 0.5) * 2.0);
    }

    private static float[] lerp(float[] a, float[] b, double t) {
        return new float[]{
                (float) (a[0] + (b[0] - a[0]) * t),
                (float) (a[1] + (b[1] - a[1]) * t),
                (float) (a[2] + (b[2] - a[2]) * t)};
    }
}
