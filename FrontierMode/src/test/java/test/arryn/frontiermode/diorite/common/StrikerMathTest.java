package test.arryn.frontiermode.diorite.common;

import com.arryn.frontiermode.diorite.common.StrikerMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void color_blends_between_the_stops_and_clamps() {
        assertArrayEquals(new float[]{0.60F, 0.65F, 0.60F}, StrikerMath.color(0.25), 1e-6F);
        assertArrayEquals(StrikerMath.color(0.0), StrikerMath.color(-4.0), 1e-6F);
        assertArrayEquals(StrikerMath.color(1.0), StrikerMath.color(4.0), 1e-6F);
    }
}
