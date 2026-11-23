package com.arryn.frontiermode.diorite.common;

/**
 * The Diorite Striker's color arithmetic (diorite-tools.md § What a strike reads), free of Minecraft
 * types so plain JUnit can test it. The color is Difficulty against the path tip's, blended blue
 * through yellow to red -- in hue, never in RGB: blue and yellow are near-complements, so a straight RGB
 * mix passes through grey (about 0.6, 0.65, 0.6), the Wilds' own sputter color. Each stop is held as
 * hue, saturation and value; the blend moves all three linearly, hue the short way round (blue about
 * 225 degrees to yellow about 53 by way of cyan and green, then yellow to red at 0), so every blend point
 * keeps its saturation.
 */
public final class StrikerMath {

    // The three stops as RGB, 0-1 (the colors the design names); the blend runs on their HSV.
    private static final float[] BLUE_HSV = hsvOf(0.20F, 0.40F, 1.00F);
    private static final float[] YELLOW_HSV = hsvOf(1.00F, 0.90F, 0.20F);
    private static final float[] RED_HSV = hsvOf(1.00F, 0.10F, 0.10F);

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

    /** The spark color (RGB, 0-1) for {@code heat}: blue at 0, yellow at 0.5, red at 1, blended in hue. */
    public static float[] color(double heat) {
        double t = Math.max(0.0, Math.min(1.0, heat));
        float[] hsv = t <= 0.5 ? lerp(BLUE_HSV, YELLOW_HSV, t * 2.0) : lerp(YELLOW_HSV, RED_HSV, (t - 0.5) * 2.0);
        return rgbOf(hsv[0], hsv[1], hsv[2]);
    }

    /** Saturation (0-1) of the color at {@code heat}; the test holds it above 0.5 so no blend point reads grey. */
    public static float saturation(double heat) {
        float[] rgb = color(heat);
        return hsvOf(rgb[0], rgb[1], rgb[2])[1];
    }

    private static float[] lerp(float[] a, float[] b, double t) {
        return new float[]{
                (float) (a[0] + (b[0] - a[0]) * t),
                (float) (a[1] + (b[1] - a[1]) * t),
                (float) (a[2] + (b[2] - a[2]) * t)};
    }

    /** RGB (0-1) to {hue in degrees 0-360, saturation 0-1, value 0-1}. */
    private static float[] hsvOf(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = 60.0F * (((g - b) / delta) % 6.0F);
        } else if (max == g) {
            hue = 60.0F * ((b - r) / delta + 2.0F);
        } else {
            hue = 60.0F * ((r - g) / delta + 4.0F);
        }
        if (hue < 0.0F) {
            hue += 360.0F;
        }
        return new float[]{hue, max == 0.0F ? 0.0F : delta / max, max};
    }

    /** {hue in degrees, saturation 0-1, value 0-1} to RGB (0-1). */
    private static float[] rgbOf(float hue, float saturation, float value) {
        float chroma = value * saturation;
        float sector = (hue % 360.0F) / 60.0F;
        float x = chroma * (1.0F - Math.abs(sector % 2.0F - 1.0F));
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        if (sector < 1.0F) {
            r = chroma; g = x;
        } else if (sector < 2.0F) {
            r = x; g = chroma;
        } else if (sector < 3.0F) {
            g = chroma; b = x;
        } else if (sector < 4.0F) {
            g = x; b = chroma;
        } else if (sector < 5.0F) {
            r = x; b = chroma;
        } else {
            r = chroma; b = x;
        }
        float m = value - chroma;
        return new float[]{r + m, g + m, b + m};
    }
}
