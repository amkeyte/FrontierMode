package test.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.BorderPregenLogic;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void insideDisk_includes_the_boundary_and_excludes_one_chunk_beyond() {
        // radius 5: (3,4) is exactly on the boundary (9 + 16 = 25), (5,1) is just past it (26).
        assertTrue(BorderPregenLogic.insideDisk(3, 4, 0, 0, 5));
        assertTrue(BorderPregenLogic.insideDisk(5, 0, 0, 0, 5));
        assertFalse(BorderPregenLogic.insideDisk(5, 1, 0, 0, 5));
        assertFalse(BorderPregenLogic.insideDisk(6, 0, 0, 0, 5));
        // Radius 0 is the center chunk alone.
        assertTrue(BorderPregenLogic.insideDisk(7, -3, 7, -3, 0));
        assertFalse(BorderPregenLogic.insideDisk(8, -3, 7, -3, 0));
    }

    @Test
    void insideDisk_of_a_smaller_disk_around_the_same_center_is_inside_the_larger_one() {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                if (BorderPregenLogic.insideDisk(x, z, 2, 2, 3)) {
                    assertTrue(BorderPregenLogic.insideDisk(x, z, 2, 2, 6), x + "," + z);
                }
            }
        }
        // And a chunk in the larger disk's ring is not in the smaller.
        assertTrue(BorderPregenLogic.insideDisk(8, 2, 2, 2, 6));
        assertFalse(BorderPregenLogic.insideDisk(8, 2, 2, 2, 3));
    }

    @Test
    void insideDisk_follows_the_center_when_two_disks_have_different_centers() {
        // Disk A at (0,0) r4, disk B at (3,0) r4: (6,0) is only in B, (-4,0) only in A, (1,0) in both.
        assertFalse(BorderPregenLogic.insideDisk(6, 0, 0, 0, 4));
        assertTrue(BorderPregenLogic.insideDisk(6, 0, 3, 0, 4));
        assertTrue(BorderPregenLogic.insideDisk(-4, 0, 0, 0, 4));
        assertFalse(BorderPregenLogic.insideDisk(-4, 0, 3, 0, 4));
        assertTrue(BorderPregenLogic.insideDisk(1, 0, 0, 0, 4));
        assertTrue(BorderPregenLogic.insideDisk(1, 0, 3, 0, 4));
    }

    @Test
    void insideDisk_agrees_with_membership_in_chunkOffsetsInDisk() {
        int radius = 4;
        Set<String> members = new HashSet<>();
        for (int[] o : BorderPregenLogic.chunkOffsetsInDisk(radius)) {
            members.add(o[0] + "," + o[1]);
        }
        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                assertEquals(members.contains(dx + "," + dz),
                        BorderPregenLogic.insideDisk(10 + dx, -5 + dz, 10, -5, radius),
                        dx + "," + dz);
            }
        }
    }

    @Test
    void insideDisk_rejects_a_negative_radius() {
        assertThrows(IllegalArgumentException.class, () -> BorderPregenLogic.insideDisk(0, 0, 0, 0, -1));
    }

    @Test
    void offsetsFor_is_the_same_sequence_as_chunkOffsetsInDisk_and_is_reused() {
        List<int[]> cached = BorderPregenLogic.offsetsFor(5);
        List<int[]> fresh = BorderPregenLogic.chunkOffsetsInDisk(5);

        assertEquals(fresh.size(), cached.size());
        for (int i = 0; i < fresh.size(); i++) {
            assertEquals(fresh.get(i)[0], cached.get(i)[0]);
            assertEquals(fresh.get(i)[1], cached.get(i)[1]);
        }
        assertSame(cached, BorderPregenLogic.offsetsFor(5));
        assertThrows(UnsupportedOperationException.class, () -> cached.add(new int[] {0, 0}));
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
