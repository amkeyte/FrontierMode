package test.arryn.frontiermode.boss.common;

import com.arryn.frontiermode.boss.common.KeeperLootTables;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // ---- The reward past path position 20 (FRO_242): 1 + step x milestones passed beyond 20 ----

    private static double multiplierAt(int p) {
        return KeeperLootTables.multiplier(OptionalInt.of(p), 5, 0.3);
    }

    @Test
    void the_multiplier_is_flat_through_twenty_and_between_milestones() {
        for (int p : new int[]{1, 15, 20, 21, 24}) {
            assertEquals(1.0, multiplierAt(p), 1e-9, "position " + p);
        }
    }

    @Test
    void the_multiplier_steps_at_each_milestone_past_twenty() {
        assertEquals(1.3, multiplierAt(25), 1e-9);
        assertEquals(1.3, multiplierAt(29), 1e-9);
        assertEquals(1.6, multiplierAt(30), 1e-9);
        assertEquals(1.9, multiplierAt(35), 1e-9);
        assertEquals(2.2, multiplierAt(40), 1e-9);
    }

    @Test
    void a_keeper_with_no_path_position_gets_no_multiplier() {
        assertEquals(1.0, KeeperLootTables.multiplier(OptionalInt.empty(), 5, 0.3), 1e-9);
    }

    @Test
    void a_zero_step_grows_nothing() {
        assertEquals(1.0, KeeperLootTables.multiplier(OptionalInt.of(40), 5, 0.0), 1e-9);
    }

    @Test
    void a_retuned_wave_moves_the_multiplier_steps_with_its_milestones() {
        // Wave length 6: milestones at 6, 12, 18, 24, 30. Twenty falls inside the fourth wave, so the
        // first step is the next milestone, 24 -- not 25.
        assertEquals(1.0, KeeperLootTables.multiplier(OptionalInt.of(23), 6, 0.3), 1e-9);
        assertEquals(1.3, KeeperLootTables.multiplier(OptionalInt.of(24), 6, 0.3), 1e-9);
        assertEquals(1.3, KeeperLootTables.multiplier(OptionalInt.of(29), 6, 0.3), 1e-9);
        assertEquals(1.6, KeeperLootTables.multiplier(OptionalInt.of(30), 6, 0.3), 1e-9);
    }

    @Test
    void counts_round_down_per_stack() {
        assertEquals(5, KeeperLootTables.scaledCount(4, multiplierAt(25)));
        assertEquals(10, KeeperLootTables.scaledCount(8, multiplierAt(25)));
        assertEquals(17, KeeperLootTables.scaledCount(8, multiplierAt(40)));
        assertEquals(0, KeeperLootTables.scaledCount(0, multiplierAt(40)));
    }

    @Test
    void a_single_item_stays_one_until_the_multiplier_reaches_two() {
        assertEquals(1, KeeperLootTables.scaledCount(1, multiplierAt(25)));
        assertEquals(1, KeeperLootTables.scaledCount(1, multiplierAt(35)));
        assertEquals(2, KeeperLootTables.scaledCount(1, multiplierAt(40)));
    }

    @Test
    void a_product_that_is_whole_on_paper_is_not_rounded_down_a_notch() {
        // 10 x 1.3 and 10 x 1.9 are whole numbers; floating point can land a hair under them.
        assertEquals(13, KeeperLootTables.scaledCount(10, multiplierAt(25)));
        assertEquals(19, KeeperLootTables.scaledCount(10, multiplierAt(35)));
        // The Tunable reads as a float and widens to a double.
        double widened = KeeperLootTables.multiplier(OptionalInt.of(35), 5, (double) 0.3f);
        assertEquals(19, KeeperLootTables.scaledCount(10, widened));
    }

    @Test
    void only_the_charts_wave_and_milestone_tables_grow() {
        for (String name : List.of("wave_1", "wave_4", "milestone_5", "milestone_20", "milestone_late")) {
            assertTrue(KeeperLootTables.growsPastTwenty(name), name);
        }
        for (String name : List.of("sapling_first", "sapling_early", "killer_bunny", KeeperLootTables.EXTRA_PIECE)) {
            assertFalse(KeeperLootTables.growsPastTwenty(name), name);
        }
    }

    @Test
    void a_grown_stack_is_split_into_full_stacks_then_the_remainder() {
        assertEquals(List.of(17), KeeperLootTables.splitCounts(17, 64));
        assertEquals(List.of(16, 1), KeeperLootTables.splitCounts(17, 16));
        assertEquals(List.of(64, 64, 2), KeeperLootTables.splitCounts(130, 64));
        assertEquals(List.of(), KeeperLootTables.splitCounts(0, 64));
    }

    @Test
    void a_repeated_roll_is_rerolled_until_it_differs() {
        AtomicInteger calls = new AtomicInteger();
        int[] sequence = {1, 1, 2, 3};
        int result = KeeperLootTables.rollDistinct(() -> sequence[calls.getAndIncrement()], v -> v == 1, 8);
        assertEquals(2, result);
        assertEquals(3, calls.get());
    }

    @Test
    void a_roll_that_never_differs_is_accepted_after_the_bound() {
        AtomicInteger calls = new AtomicInteger();
        int result = KeeperLootTables.rollDistinct(calls::incrementAndGet, v -> true, KeeperLootTables.MAX_EXTRA_REROLLS);
        // The first roll plus eight re-rolls; the last one stands.
        assertEquals(KeeperLootTables.MAX_EXTRA_REROLLS + 1, calls.get());
        assertEquals(KeeperLootTables.MAX_EXTRA_REROLLS + 1, result);
    }

    @Test
    void a_roll_that_is_already_distinct_is_not_rerolled() {
        AtomicInteger calls = new AtomicInteger();
        KeeperLootTables.rollDistinct(calls::incrementAndGet, v -> false, 8);
        assertEquals(1, calls.get());
    }
}
