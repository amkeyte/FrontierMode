package com.arryn.frontiermode.difficulty.common;

/**
 * The stat ladder's arithmetic (difficulty.md § The stat ladder), free of Minecraft types so plain
 * JUnit can test it. Every rung is {@code 1 + growth x (n - 1)}, so {@code n = 1} is exactly vanilla.
 */
public final class LadderMath {

    private LadderMath() {
    }

    /** One rung's multiplier: {@code 1 + growth x (n - 1)}. Never below 1 for {@code n >= 1}. */
    public static double multiplier(double growth, int n) {
        return 1.0 + growth * (n - 1);
    }

    /**
     * A Sentinel's growth: the ordinary growth plus a Sentinel share rising from {@code far} at
     * intensity 0 to {@code near} at intensity 1 (beside the boss). Intensity is clamped to [0, 1].
     */
    public static double sentinelGrowth(double ordinary, double far, double near, double intensity) {
        double t = Math.max(0.0, Math.min(1.0, intensity));
        return ordinary + far + (near - far) * t;
    }
}
