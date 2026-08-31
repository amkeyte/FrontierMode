package test.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.BorderMathLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RM_FRO_026 ("Dorothy")'s own done-bar line: {@code BorderMath.distanceTo()/direction()} exist
 * and are unit-exercised against at least one real pair of points. Mirrors
 * {@code MobReconcileLogicTest}/{@code TickThrottleTest}'s plain-JUnit, no-mocking style --
 * targets {@link BorderMathLogic} directly rather than {@code BorderMath} itself, since the
 * latter's {@code BlockPos} parameters aren't on this project's test classpath.
 */
class BorderMathLogicTest {

    private static final double DELTA = 1e-9;

    @Test
    void distanceTo_is_zero_for_coincident_points() {
        assertEquals(0.0, BorderMathLogic.distanceTo(5, 5, 5, 5), DELTA);
    }

    @Test
    void distanceTo_matches_a_real_3_4_5_pair() {
        // (0,0) -> (3,4): classic 3-4-5 right triangle, exact in double precision.
        assertEquals(5.0, BorderMathLogic.distanceTo(0, 0, 3, 4), DELTA);
    }

    @Test
    void distanceTo_ignores_direction_travelled() {
        assertEquals(
                BorderMathLogic.distanceTo(0, 0, 3, 4),
                BorderMathLogic.distanceTo(3, 4, 0, 0),
                DELTA
        );
    }

    @Test
    void direction_is_zero_vector_for_coincident_points() {
        assertArrayEquals(new double[] {0.0, 0.0}, BorderMathLogic.direction(7, -2, 7, -2), DELTA);
    }

    @Test
    void direction_is_a_unit_vector_toward_the_target() {
        // (0,0) -> (3,4): same 3-4-5 pair, normalized.
        assertArrayEquals(new double[] {0.6, 0.8}, BorderMathLogic.direction(0, 0, 3, 4), DELTA);
    }

    @Test
    void direction_along_a_single_axis_is_the_unit_axis_vector() {
        assertArrayEquals(new double[] {1.0, 0.0}, BorderMathLogic.direction(0, 0, 10, 0), DELTA);
        assertArrayEquals(new double[] {0.0, -1.0}, BorderMathLogic.direction(0, 0, 0, -10), DELTA);
    }
}
