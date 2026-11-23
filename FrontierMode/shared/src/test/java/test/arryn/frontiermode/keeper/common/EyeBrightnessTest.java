package test.arryn.frontiermode.keeper.common;

import com.arryn.frontiermode.keeper.common.EyeBrightness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** keeper.md § Client-side eyes and flash: the brightness of a Keeper's eyes by health. */
class EyeBrightnessTest {

    private static final float EPSILON = 1.0E-6F;

    @Test
    void full_health_is_full_brightness() {
        assertEquals(1.0F, EyeBrightness.of(20.0F, 20.0F), EPSILON);
    }

    @Test
    void no_health_is_the_floor() {
        assertEquals(EyeBrightness.FLOOR, EyeBrightness.of(0.0F, 20.0F), EPSILON);
    }

    @Test
    void half_health_is_the_midpoint() {
        assertEquals((1.0F + EyeBrightness.FLOOR) / 2.0F, EyeBrightness.of(10.0F, 20.0F), EPSILON);
    }

    @Test
    void health_above_the_maximum_clamps_to_full() {
        assertEquals(1.0F, EyeBrightness.of(30.0F, 20.0F), EPSILON);
    }

    @Test
    void health_below_zero_clamps_to_the_floor() {
        assertEquals(EyeBrightness.FLOOR, EyeBrightness.of(-5.0F, 20.0F), EPSILON);
    }
}
