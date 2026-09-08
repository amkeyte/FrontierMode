package test.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.FrontierSicknessLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RM_FRO_037 ("Brenda")'s own done-bar line: Frontier Sickness's severity climb and Sick
 * Wildlife's density multiplier are unit-exercised, plain-JUnit style, same shape as
 * {@code BorderMathLogicTest}/{@code BorderCurveMathTest}.
 */
class FrontierSicknessLogicTest {

    private static final double DELTA = 1e-9;

    @Test
    void severityTarget_is_zero_when_not_in_the_exterior() {
        assertEquals(0.0, FrontierSicknessLogic.severityTarget(0, 1.0), DELTA);
    }

    @Test
    void severityTarget_scales_linearly_with_distance() {
        assertEquals(50.0, FrontierSicknessLogic.severityTarget(100, 0.5), DELTA);
    }

    @Test
    void climbSeverity_moves_toward_the_target_without_snapping() {
        double next = FrontierSicknessLogic.climbSeverity(0.0, 100.0, 0.1);
        assertEquals(10.0, next, DELTA);
        assertTrue(next < 100.0, "one tick of catch-up should not reach the target outright");
    }

    @Test
    void climbSeverity_approaches_the_target_over_repeated_ticks() {
        double severity = 0.0;
        for (int i = 0; i < 200; i++) {
            severity = FrontierSicknessLogic.climbSeverity(severity, 100.0, 0.1);
        }
        assertEquals(100.0, severity, 1e-6);
    }

    @Test
    void climbSeverity_is_symmetric_and_decays_back_toward_a_lower_target() {
        double severity = FrontierSicknessLogic.climbSeverity(100.0, 0.0, 0.1);
        assertEquals(90.0, severity, DELTA);
    }

    @Test
    void climbSeverity_never_goes_negative() {
        assertEquals(0.0, FrontierSicknessLogic.climbSeverity(0.05, 0.0, 1.0), DELTA);
    }

    @Test
    void wildlifeDensityMultiplier_is_flat_outside_the_exterior() {
        assertEquals(1.0, FrontierSicknessLogic.wildlifeDensityMultiplier(0, 10.0, 2.0), DELTA);
    }

    @Test
    void wildlifeDensityMultiplier_rises_quickly_near_the_frontier_edge() {
        // At one falloff-distance out, the rise term (1 - e^-1) is already ~63% of the way from
        // 1.0 to maxMultiplier -- "pronounced right at the edge," not a slow climb.
        double atOneFalloff = FrontierSicknessLogic.wildlifeDensityMultiplier(10, 10.0, 2.0);
        assertEquals(1.0 + (2.0 - 1.0) * (1.0 - Math.exp(-1.0)), atOneFalloff, DELTA);
        assertTrue(atOneFalloff > 1.5, "should already be well past halfway to the flat multiplier one falloff-distance out");
    }

    @Test
    void wildlifeDensityMultiplier_flattens_toward_the_max_multiplier_far_out() {
        double farOut = FrontierSicknessLogic.wildlifeDensityMultiplier(10_000, 10.0, 2.0);
        assertEquals(2.0, farOut, 1e-6);
    }
}
