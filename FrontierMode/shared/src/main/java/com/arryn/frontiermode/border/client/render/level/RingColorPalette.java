package com.arryn.frontiermode.border.client.render.level;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public final class RingColorPalette {

    private static final List<Vector3f> cache = new ArrayList<>();
    private static int maxGenerated = -1;

    private RingColorPalette() {}

    public static Vector3f get(int layer) {
        if (layer < 0) return new Vector3f(1, 1, 1);

        if (layer > maxGenerated)
            generate(layer);

        return cache.get(layer);
    }

    private static void generate(int maxLayer) {
        cache.clear();
        for (int i = 0; i <= maxLayer; i++)
            cache.add(compute(i, maxLayer));
        maxGenerated = maxLayer;
    }

    private static Vector3f compute(int layer, int maxLayer) {
        if (maxLayer <= 0 || layer <= 0)
            return new Vector3f(1, 1, 1);

        float t = layer / (float) maxLayer;
        float hue = t * 270f; // purple→blue gradient
        float sat = 1f;
        float val = 1f;

        return hsvToRgb(hue, sat, val);
    }

    private static Vector3f hsvToRgb(float hueDeg, float sat, float val) {
        float h = hueDeg / 60f;
        float c = val * sat;
        float x = c * (1 - Math.abs(h % 2 - 1));
        float m = val - c;

        float r=0,g=0,b=0;

        if (0 <= h && h < 1) { r=c; g=x; b=0; }
        else if (1 <= h && h < 2) { r=x; g=c; b=0; }
        else if (2 <= h && h < 3) { r=0; g=c; b=x; }
        else if (3 <= h && h < 4) { r=0; g=x; b=c; }
        else if (4 <= h && h < 5) { r=x; g=0; b=c; }
        else if (5 <= h && h < 6) { r=c; g=0; b=x; }

        return new Vector3f(r+m, g+m, b+m);
    }
}
