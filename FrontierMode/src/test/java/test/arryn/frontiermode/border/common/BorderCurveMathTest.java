package test.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.BorderCurveMath;
import com.arryn.frontiermode.border.common.fixture.Shape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RM_FRO_027 ("Janet")'s own done-bar line: at least one curve evaluates correctly end-to-end
 * against {@code BorderMath} for a real distance value. Mirrors {@code BorderMathLogicTest}'s
 * plain-JUnit style -- targets {@link BorderCurveMath} directly since {@code BorderMath} itself
 * isn't on this project's test classpath (see {@code BorderMathLogic}'s own doc for why).
 */
class BorderCurveMathTest {

    private static final double DELTA = 1e-9;

    @Test
    void linear_is_max_at_center_and_zero_at_the_edge() {
        assertEquals(1.0, BorderCurveMath.intensityAt(Shape.LINEAR, 1.0, 0.0), DELTA);
        assertEquals(0.0, BorderCurveMath.intensityAt(Shape.LINEAR, 1.0, 1.0), DELTA);
        assertEquals(0.5, BorderCurveMath.intensityAt(Shape.LINEAR, 1.0, 0.5), DELTA);
    }

    @Test
    void square_is_max_at_center_and_zero_at_the_edge() {
        assertEquals(1.0, BorderCurveMath.intensityAt(Shape.SQUARE, 1.0, 0.0), DELTA);
        assertEquals(0.0, BorderCurveMath.intensityAt(Shape.SQUARE, 1.0, 1.0), DELTA);
        // Worked Guardian Mobs example's "difficulty" curve is LOG, but SQUARE's own convex
        // falloff (stays high longer, then drops late) should still sit above the LINEAR
        // baseline at the same halfway distance.
        assertTrue(BorderCurveMath.intensityAt(Shape.SQUARE, 1.0, 0.5)
                > BorderCurveMath.intensityAt(Shape.LINEAR, 1.0, 0.5));
    }

    @Test
    void log_is_max_at_center_and_zero_at_the_edge() {
        assertEquals(1.0, BorderCurveMath.intensityAt(Shape.LOG, 2.0, 0.0), DELTA);
        assertEquals(0.0, BorderCurveMath.intensityAt(Shape.LOG, 2.0, 1.0), DELTA);
    }

    @Test
    void log_spikes_close_to_center_then_flattens_toward_the_edge() {
        // Border Curve's own worked example: LOG "climbs logarithmically (a sharper spike close
        // to the boss, flattening further out)" -- i.e. the drop from d=0 to d=0.1 (near center)
        // should be larger than the drop from d=0.9 to d=1.0 (near the edge), the concave shape
        // BorderCurveMath's own doc describes.
        double steepness = 5.0;
        double dropNearCenter = BorderCurveMath.intensityAt(Shape.LOG, steepness, 0.0)
                - BorderCurveMath.intensityAt(Shape.LOG, steepness, 0.1);
        double dropNearEdge = BorderCurveMath.intensityAt(Shape.LOG, steepness, 0.9)
                - BorderCurveMath.intensityAt(Shape.LOG, steepness, 1.0);

        assertTrue(dropNearCenter > dropNearEdge);
    }

    @Test
    void normalized_distance_beyond_the_edge_reads_as_the_edge() {
        assertEquals(
                BorderCurveMath.intensityAt(Shape.LINEAR, 1.0, 1.0),
                BorderCurveMath.intensityAt(Shape.LINEAR, 1.0, 5.0),
                DELTA
        );
    }
}
