package test.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.BorderPregenLogic;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RM_FRO_028 ("Diane")'s own done-bar line requires a border's disk to pregenerate via a
 * deterministic, resumable enumeration. Mirrors {@code BorderMathLogicTest}'s plain-JUnit style.
 */
class BorderPregenLogicTest {

    @Test
    void radius_zero_is_just_the_center_chunk() {
        List<int[]> offsets = BorderPregenLogic.chunkOffsetsInDisk(0);
        assertEquals(1, offsets.size());
        assertEquals(0, offsets.get(0)[0]);
        assertEquals(0, offsets.get(0)[1]);
    }

    @Test
    void radius_one_is_the_center_plus_its_four_cardinal_neighbors() {
        // (0,0)=0, (±1,0)/(0,±1)=1 (<=1, included), the four diagonal corners=2 (>1, excluded).
        List<int[]> offsets = BorderPregenLogic.chunkOffsetsInDisk(1);
        assertEquals(5, offsets.size());
    }

    @Test
    void the_center_chunk_is_always_first() {
        List<int[]> offsets = BorderPregenLogic.chunkOffsetsInDisk(4);
        assertEquals(0, offsets.get(0)[0]);
        assertEquals(0, offsets.get(0)[1]);
    }

    @Test
    void every_offset_is_within_the_requested_radius_and_unique() {
        int radius = 6;
        List<int[]> offsets = BorderPregenLogic.chunkOffsetsInDisk(radius);

        Set<String> seen = new HashSet<>();
        for (int[] o : offsets) {
            assertTrue((long) o[0] * o[0] + (long) o[1] * o[1] <= (long) radius * radius);
            assertTrue(seen.add(o[0] + "," + o[1]), "duplicate offset: " + o[0] + "," + o[1]);
        }
    }

    @Test
    void ordering_is_non_decreasing_by_distance_from_center() {
        List<int[]> offsets = BorderPregenLogic.chunkOffsetsInDisk(6);

        long previousDistSq = -1;
        for (int[] o : offsets) {
            long distSq = (long) o[0] * o[0] + (long) o[1] * o[1];
            assertTrue(distSq >= previousDistSq);
            previousDistSq = distSq;
        }
    }

    @Test
    void the_enumeration_is_deterministic_across_repeated_calls() {
        List<int[]> first = BorderPregenLogic.chunkOffsetsInDisk(5);
        List<int[]> second = BorderPregenLogic.chunkOffsetsInDisk(5);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i)[0], second.get(i)[0]);
            assertEquals(first.get(i)[1], second.get(i)[1]);
        }
    }

    @Test
    void negative_radius_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> BorderPregenLogic.chunkOffsetsInDisk(-1));
    }

    @Test
    void chunkRadiusFor_rounds_up_to_the_next_whole_chunk() {
        assertEquals(0, BorderPregenLogic.chunkRadiusFor(0));
        assertEquals(1, BorderPregenLogic.chunkRadiusFor(1));
        assertEquals(1, BorderPregenLogic.chunkRadiusFor(16));
        assertEquals(2, BorderPregenLogic.chunkRadiusFor(17));
        assertEquals(32, BorderPregenLogic.chunkRadiusFor(512));
    }
}
