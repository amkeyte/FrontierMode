package test.arryn.frontiermode.structure.common;

import com.arryn.frontiermode.structure.common.CairnPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** cairn.md § Data model and § Placement: how many Cairns a boss gets, and where they may go. */
class CairnPlacementTest {

    private static int countAt(int pathPosition) {
        return CairnPlacement.count(OptionalInt.of(pathPosition));
    }

    @Test
    void no_cairn_before_path_position_twenty() {
        for (int p : new int[]{1, 5, 14, 15, 19}) {
            assertEquals(0, countAt(p), "position " + p);
        }
    }

    @Test
    void one_cairn_at_twenty_and_one_more_on_each_milestone() {
        assertEquals(1, countAt(20));
        assertEquals(1, countAt(24));
        assertEquals(2, countAt(25));
        assertEquals(2, countAt(29));
        assertEquals(3, countAt(30));
        assertEquals(17, countAt(100));
    }

    @Test
    void a_boss_with_no_path_position_gets_none() {
        assertEquals(0, CairnPlacement.count(OptionalInt.empty()));
    }

    @Test
    void the_band_is_half_to_all_of_the_home_borders_radius() {
        assertEquals(4, CairnPlacement.innerRadius(8));
        assertEquals(8, CairnPlacement.outerRadius(8));
        assertEquals(100, CairnPlacement.innerRadius(200));
        assertEquals(200, CairnPlacement.outerRadius(200));
    }

    @Test
    void the_band_never_inverts_even_on_a_tiny_border() {
        for (int radius = 1; radius <= 64; radius++) {
            assertTrue(CairnPlacement.innerRadius(radius) <= CairnPlacement.outerRadius(radius), "radius " + radius);
        }
    }

    @Test
    void a_footprint_inside_one_chunk_touches_only_that_chunk() {
        assertEquals(List.of(new ChunkPos(0, 0)), CairnPlacement.footprintChunks(new BlockPos(8, 70, 8), 3));
    }

    @Test
    void a_footprint_on_a_chunk_edge_touches_both_sides() {
        assertEquals(List.of(new ChunkPos(0, 0), new ChunkPos(1, 0)),
                CairnPlacement.footprintChunks(new BlockPos(16, 70, 5), 3));
    }

    @Test
    void a_footprint_on_a_chunk_corner_touches_four_chunks() {
        assertEquals(
                List.of(new ChunkPos(0, 0), new ChunkPos(0, 1), new ChunkPos(1, 0), new ChunkPos(1, 1)),
                CairnPlacement.footprintChunks(new BlockPos(15, 70, 15), 3));
    }

    @Test
    void negative_coordinates_round_toward_the_chunk_below() {
        assertEquals(
                List.of(new ChunkPos(-1, -1), new ChunkPos(-1, 0), new ChunkPos(0, -1), new ChunkPos(0, 0)),
                CairnPlacement.footprintChunks(new BlockPos(-1, 70, -1), 3));
    }
}
