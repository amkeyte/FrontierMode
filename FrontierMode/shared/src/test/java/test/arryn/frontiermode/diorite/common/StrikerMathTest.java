package test.arryn.frontiermode.diorite.common;

import com.arryn.frontiermode.diorite.common.StrikerMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** diorite-tools.md § What a strike reads: heat is Difficulty against the tip's; color runs blue-yellow-red. */
class StrikerMathTest {

    @Test
    void every_strike_is_blue_while_the_tip_is_level_one() {
        assertEquals(0.0, StrikerMath.heat(1, 1));
        assertEquals(0.0, StrikerMath.heat(5, 1));
        assertEquals(0.0, StrikerMath.heat(1, 0));
    }

    @Test
    void heat_runs_from_the_first_layer_to_the_tip() {
        assertEquals(0.0, StrikerMath.heat(1, 11));
        assertEquals(0.5, StrikerMath.heat(6, 11), 1e-9);
        assertEquals(1.0, StrikerMath.heat(11, 11));
    }

    @Test
    void heat_is_clamped() {
        assertEquals(1.0, StrikerMath.heat(30, 11));
        assertEquals(0.0, StrikerMath.heat(0, 11));
    }

    @Test
    void color_hits_blue_yellow_red_at_the_ends_and_middle() {
        assertArrayEquals(new float[]{0.20F, 0.40F, 1.00F}, StrikerMath.color(0.0), 1e-6F);
        assertArrayEquals(new float[]{1.00F, 0.90F, 0.20F}, StrikerMath.color(0.5), 1e-6F);
        assertArrayEquals(new float[]{1.00F, 0.10F, 0.10F}, StrikerMath.color(1.0), 1e-6F);
    }

    @Test
    void color_clamps_outside_zero_to_one() {
        assertArrayEquals(StrikerMath.color(0.0), StrikerMath.color(-4.0), 1e-6F);
        assertArrayEquals(StrikerMath.color(1.0), StrikerMath.color(4.0), 1e-6F);
    }

    /** FRO_211 #29: a straight RGB mix of blue and yellow is grey (the Wilds' sputter); a hue blend never is. */
    @Test
    void no_blend_point_reads_grey() {
        for (int i = 0; i <= 100; i++) {
            double heat = i / 100.0;
            assertTrue(StrikerMath.saturation(heat) >= 0.5F, "grey at heat " + heat);
        }
    }

    @Test
    void ground_a_fifth_of_the_way_to_the_tip_is_not_grey() {
        float[] rgb = StrikerMath.color(0.2);
        float spread = Math.max(rgb[0], Math.max(rgb[1], rgb[2])) - Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        assertTrue(spread >= 0.4F, "channels too close: " + java.util.Arrays.toString(rgb));
    }

    @Test
    void blue_to_yellow_passes_through_cyan_and_green() {
        float[] cyanish = StrikerMath.color(0.2);
        assertTrue(cyanish[2] > cyanish[0], "blue-side blend should still lean blue/cyan, not red");
        float[] green = StrikerMath.color(0.375);
        assertTrue(green[1] > green[0] && green[1] > green[2], "midway blue to yellow should be green");
    }
}
