package test.arryn.frontiermode.boss.common;

import com.arryn.frontiermode.boss.common.KeeperLootTables;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** boss.md § Defeat loot: which tables a Keeper rolls, at the default wave length of 5. */
class KeeperLootTablesTest {

    private static List<String> at(int p, String body) {
        return KeeperLootTables.names(OptionalInt.of(p), body, 5);
    }

    @Test
    void the_rabbit_pays_wave_one_and_guaranteed_saplings() {
        assertEquals(List.of("wave_1", "sapling_first"), at(1, "rabbit"));
    }

    @Test
    void spiders_two_to_four_pay_wave_one_and_a_sapling_chance() {
        for (int p = 2; p <= 4; p++) {
            assertEquals(List.of("wave_1", "sapling_early"), at(p, "spider"), "position " + p);
        }
    }

    @Test
    void everyday_keepers_use_their_waves_table_and_waves_past_four_reuse_it() {
        assertEquals(List.of("wave_2"), at(6, "zombie"));
        assertEquals(List.of("wave_2"), at(9, "skeleton"));
        assertEquals(List.of("wave_3"), at(11, "zombified_piglin"));
        assertEquals(List.of("wave_3"), at(14, "pillager"));
        assertEquals(List.of("wave_4"), at(16, "pillager"));
        assertEquals(List.of("wave_4"), at(19, "vindicator"));
        assertEquals(List.of("wave_4"), at(21, "zombie"));
        assertEquals(List.of("wave_4"), at(41, "zombie"));
    }

    @Test
    void milestones_five_to_twenty_have_their_own_tables() {
        assertEquals(List.of("milestone_5"), at(5, "zombie"));
        assertEquals(List.of("milestone_10"), at(10, "skeleton"));
        assertEquals(List.of("milestone_15"), at(15, "pillager"));
        assertEquals(List.of("milestone_20"), at(20, "evoker"));
    }

    @Test
    void later_milestones_share_the_placeholder_table() {
        assertEquals(List.of("milestone_late"), at(25, "zombie"));
        assertEquals(List.of("milestone_late"), at(30, "skeleton"));
    }

    @Test
    void a_killer_bunny_adds_the_hoe_on_top_of_whatever_it_rolls() {
        assertEquals(List.of("wave_4", "killer_bunny"), at(26, "killer_bunny"));
        assertEquals(List.of("milestone_late", "killer_bunny"), at(25, "killer_bunny"));
    }

    @Test
    void a_boss_with_no_path_position_rolls_wave_one_only() {
        assertEquals(List.of("wave_1"), KeeperLootTables.names(OptionalInt.empty(), "zombie", 5));
        assertEquals(List.of("wave_1", "killer_bunny"), KeeperLootTables.names(OptionalInt.empty(), "killer_bunny", 5));
    }

    @Test
    void a_retuned_wave_moves_the_milestones_and_the_wave_numbers() {
        // Wave length 2: milestones at 2, 4, 6...; only 20 has a table of its own.
        assertEquals(List.of("milestone_late", "sapling_early"), KeeperLootTables.names(OptionalInt.of(2), "spider", 2));
        // Position 3 is still in the 2-4 sapling range whatever the wave length.
        assertEquals(List.of("wave_2", "sapling_early"), KeeperLootTables.names(OptionalInt.of(3), "zombie", 2));
        assertEquals(List.of("wave_3"), KeeperLootTables.names(OptionalInt.of(5), "zombie", 2));
        assertEquals(List.of("milestone_20"), KeeperLootTables.names(OptionalInt.of(20), "evoker", 2));
    }
}
