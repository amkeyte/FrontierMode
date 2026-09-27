package test.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.BorderMathLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FRO_190: the growth-wave radii quoted on border.md § Radius: growth in waves, at the default
 * Tunables (base 20, amplitude 0.5, wave length 5). Plain JUnit against {@link BorderMathLogic},
 * like {@code BorderMathLogicTest}.
 */
class BorderWaveRadiusTest {

    private static final int BASE = 20;
    private static final double AMPLITUDE = 0.5;
    private static final int WAVE_LENGTH = 5;

    private static long radiusAt(int pathPosition) {
        return BorderMathLogic.waveRadius(BASE, AMPLITUDE, WAVE_LENGTH, pathPosition);
    }

    @Test
    void default_radii_for_positions_1_to_11() {
        long[] expected = {30, 43, 54, 57, 57, 86, 123, 154, 165, 165, 247};
        for (int p = 1; p <= expected.length; p++) {
            assertEquals(expected[p - 1], radiusAt(p), "position " + p);
        }
    }

    @Test
    void last_step_of_each_wave_is_flat() {
        assertEquals(radiusAt(4), radiusAt(5));
        assertEquals(radiusAt(9), radiusAt(10));
    }

    @Test
    void position_below_1_is_treated_as_1() {
        assertEquals(radiusAt(1), radiusAt(0));
    }

    @Test
    void zero_amplitude_never_grows() {
        assertEquals(BASE, BorderMathLogic.waveRadius(BASE, 0.0, WAVE_LENGTH, 12));
    }

    @Test
    void two_step_wave_alternates_full_step_and_flat() {
        // Length 2: factors 1 + A, then 1.0.
        assertEquals(30, BorderMathLogic.waveRadius(BASE, AMPLITUDE, 2, 1));
        assertEquals(30, BorderMathLogic.waveRadius(BASE, AMPLITUDE, 2, 2));
        assertEquals(45, BorderMathLogic.waveRadius(BASE, AMPLITUDE, 2, 3));
    }
}
