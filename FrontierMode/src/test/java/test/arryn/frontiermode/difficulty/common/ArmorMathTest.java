package test.arryn.frontiermode.difficulty.common;

import com.arryn.frontiermode.difficulty.common.ArmorMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * difficulty.md § Armor answers back: the capability of each vanilla kit at the default reference hit of 10, and the
 * multiplier. Plain JUnit against {@link ArmorMath}.
 */
class ArmorMathTest {

    private static final double HIT = 10.0;
    private static final double TOLERANCE = 0.001;

    private static double capability(double armor, double toughness, double protection) {
        return ArmorMath.capability(armor, toughness, protection, HIT);
    }

    @Test
    void bare_skin_is_zero_the_best_kit_is_one_and_a_kit_past_it_clamps_to_one() {
        assertEquals(0.0, capability(0, 0, 0), TOLERANCE);
        assertEquals(1.0, capability(20, 12, 16), TOLERANCE);
        assertEquals(1.0, capability(30, 20, 20), TOLERANCE);
    }

    @Test
    void each_vanilla_kit_without_enchantments() {
        assertEquals(0.089, capability(7, 0, 0), TOLERANCE);    // leather
        assertEquals(0.267, capability(11, 0, 0), TOLERANCE);   // gold
        assertEquals(0.311, capability(12, 0, 0), TOLERANCE);   // chainmail
        assertEquals(0.445, capability(15, 0, 0), TOLERANCE);   // iron
        assertEquals(0.778, capability(20, 8, 0), TOLERANCE);   // diamond
        assertEquals(0.801, capability(20, 12, 0), TOLERANCE);  // netherite
        assertEquals(0.992, capability(20, 8, 16), TOLERANCE);  // diamond, Protection IV on every piece
    }

    @Test
    void one_piece_counts_for_a_little_and_protection_with_no_armor_counts_for_nothing() {
        assertEquals(0.053, capability(6, 0, 0), TOLERANCE);    // one iron chestplate
        assertEquals(0.0, capability(0, 0, 16), TOLERANCE);
    }

    @Test
    void protection_counts_in_proportion_to_the_armor_it_sits_on() {
        assertEquals(0.318, capability(7, 0, 16), TOLERANCE);   // leather, Protection IV
        assertEquals(0.564, capability(11, 0, 16), TOLERANCE);  // gold
        assertEquals(0.619, capability(12, 0, 16), TOLERANCE);  // chainmail
        assertEquals(0.765, capability(15, 0, 16), TOLERANCE);  // iron
        assertEquals(0.146, capability(7, 0, 4), TOLERANCE);    // leather, Protection I
    }

    @Test
    void protection_above_twenty_counts_as_twenty_before_the_armor_scaling() {
        assertEquals(capability(15, 0, 20), capability(15, 0, 30), 1e-12);
    }

    @Test
    void a_capability_at_or_below_the_expected_one_multiplies_by_one() {
        assertEquals(1.0, ArmorMath.multiplier(4.0, 0.5, 0.5), 1e-12);
        assertEquals(1.0, ArmorMath.multiplier(4.0, 0.2, 0.5), 1e-12);
    }

    @Test
    void a_counter_of_four_with_a_capability_half_above_gives_two() {
        assertEquals(2.0, ArmorMath.multiplier(4.0, 1.0, 0.5), 1e-12);
    }

    @Test
    void a_counter_of_one_always_gives_one() {
        assertEquals(1.0, ArmorMath.multiplier(1.0, 1.0, 0.0), 1e-12);
    }

    @Test
    void a_counter_below_one_is_a_programming_error() {
        assertThrows(IllegalArgumentException.class, () -> ArmorMath.multiplier(0.5, 1.0, 0.0));
    }
}
