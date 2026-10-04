package test.arryn.frontiermode.siting.common;

import com.arryn.frontiermode.siting.common.SitingMath;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** siting.md: ring candidates (where to look) and the starting-site verdict (how a disc is judged). */
class SitingMathTest {

    private static final SitingMath.Requirements NEED = new SitingMath.Requirements(5, 0.3, 10);

    @Test
    void eight_points_per_ring_nearest_ring_first() {
        List<BlockPos> ring = SitingMath.ringCandidates(BlockPos.ZERO, 100, 300);
        assertEquals(24, ring.size());
        double previous = 0;
        for (BlockPos p : ring) {
            double d = Math.sqrt(p.getX() * (double) p.getX() + p.getZ() * (double) p.getZ());
            assertTrue(d >= previous - 1.5, "rings must not go back inward: " + p);
            previous = d;
        }
    }

    @Test
    void the_first_ring_holds_the_compass_points_and_the_diagonals_at_the_step() {
        List<BlockPos> ring = SitingMath.ringCandidates(new BlockPos(1000, 64, -500), 100, 100);
        assertEquals(8, ring.size());
        assertTrue(ring.contains(new BlockPos(1000, 64, -600)));
        assertTrue(ring.contains(new BlockPos(1100, 64, -500)));
        assertTrue(ring.contains(new BlockPos(1000, 64, -400)));
        assertTrue(ring.contains(new BlockPos(900, 64, -500)));
        // Diagonals are 100 blocks away, not 100 on each axis.
        assertTrue(ring.contains(new BlockPos(1071, 64, -571)));
        assertTrue(ring.contains(new BlockPos(929, 64, -429)));
    }

    @Test
    void no_ring_reaches_past_the_limit_and_a_short_limit_gives_none() {
        for (BlockPos p : SitingMath.ringCandidates(BlockPos.ZERO, 100, 250)) {
            assertTrue(Math.sqrt(p.getX() * (double) p.getX() + p.getZ() * (double) p.getZ()) <= 250.5);
        }
        assertTrue(SitingMath.ringCandidates(BlockPos.ZERO, 100, 99).isEmpty());
    }

    @Test
    void a_disc_with_wood_land_stone_and_a_keeper_spot_passes() {
        SitingMath.Verdict v = SitingMath.judge(new SitingMath.Reading(5, 0.3, 10, true), NEED);
        assertTrue(v.passes());
        assertEquals(4.0, v.score(), 1e-9);
    }

    @Test
    void any_one_failing_requirement_fails_the_disc() {
        assertFalse(SitingMath.judge(new SitingMath.Reading(4, 0.0, 99, true), NEED).passes());
        assertFalse(SitingMath.judge(new SitingMath.Reading(9, 0.31, 99, true), NEED).passes());
        assertFalse(SitingMath.judge(new SitingMath.Reading(9, 0.0, 9, true), NEED).passes());
        assertFalse(SitingMath.judge(new SitingMath.Reading(9, 0.0, 99, false), NEED).passes());
    }

    @Test
    void a_failed_disc_scores_by_how_close_it_came() {
        double none = SitingMath.judge(new SitingMath.Reading(0, 1.0, 0, false), NEED).score();
        double close = SitingMath.judge(new SitingMath.Reading(4, 0.35, 9, true), NEED).score();
        assertTrue(close > none);
        assertTrue(close < 4.0);
    }

    @Test
    void a_zero_requirement_is_always_met() {
        SitingMath.Requirements lax = new SitingMath.Requirements(0, 1.0, 0);
        assertTrue(SitingMath.judge(new SitingMath.Reading(0, 1.0, 0, true), lax).passes());
    }
}
