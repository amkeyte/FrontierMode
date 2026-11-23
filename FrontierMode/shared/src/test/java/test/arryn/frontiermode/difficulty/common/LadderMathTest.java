package test.arryn.frontiermode.difficulty.common;

import com.arryn.frontiermode.difficulty.common.LadderMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * difficulty.md § Module shape: the zombie health table at the default Tunables (ordinary growth
 * 0.075, Sentinel share 0.01 far to 0.1 near). Plain JUnit against {@link LadderMath}.
 */
class LadderMathTest {

    private static final double ORDINARY = 0.075;
    private static final double FAR = 0.01;
    private static final double NEAR = 0.1;
    private static final double ZOMBIE = 20.0;

    private static double ordinaryHealth(int n) {
        return ZOMBIE * LadderMath.multiplier(ORDINARY, n);
    }

    private static double sentinelHealth(int pathPosition, double intensity) {
        return ZOMBIE * LadderMath.multiplier(LadderMath.sentinelGrowth(ORDINARY, FAR, NEAR, intensity), pathPosition);
    }

    @Test
    void level_one_is_exactly_vanilla() {
        assertEquals(1.0, LadderMath.multiplier(ORDINARY, 1));
        assertEquals(1.0, LadderMath.multiplier(1.0, 1));
    }

    @Test
    void ordinary_zombie_health_at_levels_1_5_10_15_20() {
        assertEquals(20.0, ordinaryHealth(1), 1e-9);
        assertEquals(26.0, ordinaryHealth(5), 1e-9);
        assertEquals(33.5, ordinaryHealth(10), 1e-9);
        assertEquals(41.0, ordinaryHealth(15), 1e-9);
        assertEquals(48.5, ordinaryHealth(20), 1e-9);
    }

    @Test
    void sentinel_beside_the_boss_at_levels_10_15_20() {
        assertEquals(51.5, sentinelHealth(10, 1.0), 1e-9);
        assertEquals(69.0, sentinelHealth(15, 1.0), 1e-9);
        assertEquals(86.5, sentinelHealth(20, 1.0), 1e-9);
    }

    @Test
    void sentinel_share_runs_far_to_near_and_never_undercuts_ordinary() {
        assertEquals(ORDINARY + FAR, LadderMath.sentinelGrowth(ORDINARY, FAR, NEAR, 0.0), 1e-12);
        assertEquals(ORDINARY + NEAR, LadderMath.sentinelGrowth(ORDINARY, FAR, NEAR, 1.0), 1e-12);
        assertEquals(ORDINARY + (FAR + NEAR) / 2, LadderMath.sentinelGrowth(ORDINARY, FAR, NEAR, 0.5), 1e-12);
    }

    @Test
    void intensity_is_clamped_to_zero_one() {
        assertEquals(LadderMath.sentinelGrowth(ORDINARY, FAR, NEAR, 0.0),
                LadderMath.sentinelGrowth(ORDINARY, FAR, NEAR, -3.0), 1e-12);
        assertEquals(LadderMath.sentinelGrowth(ORDINARY, FAR, NEAR, 1.0),
                LadderMath.sentinelGrowth(ORDINARY, FAR, NEAR, 7.0), 1e-12);
    }
}
