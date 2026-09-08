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

    // ------------------------------------------------------------------
    // FRO_099 ("Frontier Sickness core build" -- RM_FRO_037): distanceToEdge, the shared core
    // behind BorderMath.distanceToSurface()'s bugfix and BorderMath.distanceOutside(). Named
    // "a real (100, 0) point against a radius-40 circle centered at origin" as the regression
    // case for the old squared-vs-linear bug: the buggy formula
    // (distanceSqToCenter - radius = 100*100 - 40 = 9960) was wildly larger than the true answer
    // (100 - 40 = 60) for anything more than a few blocks from the edge -- this pins the correct
    // linear answer down explicitly so that bug can't silently come back.
    // ------------------------------------------------------------------

    @Test
    void distanceToEdge_is_zero_exactly_on_the_edge() {
        assertEquals(0.0, BorderMathLogic.distanceToEdge(40.0, 40), DELTA);
    }

    @Test
    void distanceToEdge_is_negative_inside_the_circle() {
        assertEquals(-15.0, BorderMathLogic.distanceToEdge(25.0, 40), DELTA);
    }

    @Test
    void distanceToEdge_is_positive_outside_the_circle_and_matches_the_real_pre_bugfix_regression_case() {
        // (100,0) against a radius-40 circle centered at (0,0): centerDistance=100, so the true
        // answer is 100-40=60 -- not the old buggy 100*100-40=9960 the squared-distance formula
        // produced.
        assertEquals(60.0, BorderMathLogic.distanceToEdge(100.0, 40), DELTA);
    }

    @Test
    void distanceToEdge_at_the_center_is_the_negative_radius() {
        assertEquals(-40.0, BorderMathLogic.distanceToEdge(0.0, 40), DELTA);
    }
}
