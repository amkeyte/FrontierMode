package test.arryn.satchel.trinket;

import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.TrinketTick.Key;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sort key of the global Trinket list (SAT_120): {@code order}, then jig registration index, then
 * scope creation sequence, then declaration index, then subscription sequence. Plain JUnit on the
 * key alone; the live ordering across two jigs is SatchelHealth's Trinket probe.
 */
class TrinketTickKeyTest {

    private static Key key(int order, int jig, long scope, int decl, long sub) {
        return new Key(order, jig, scope, decl, sub);
    }

    private static void assertBefore(Key first, Key second) {
        assertTrue(first.compareTo(second) < 0, first + " should sort before " + second);
        assertTrue(second.compareTo(first) > 0, second + " should sort after " + first);
    }

    @Test
    void order_decides_before_every_other_field() {
        assertBefore(key(Order.EARLY, 9, 9, 9, 9), key(Order.NORMAL, 0, 0, 0, 0));
        assertBefore(key(Order.NORMAL, 9, 9, 9, 9), key(Order.LATE, 0, 0, 0, 0));
    }

    @Test
    void jig_registration_index_breaks_an_order_tie() {
        assertBefore(key(0, 1, 9, 9, 9), key(0, 2, 0, 0, 0));
    }

    @Test
    void scope_creation_sequence_breaks_a_jig_tie() {
        assertBefore(key(0, 1, 3, 9, 9), key(0, 1, 4, 0, 0));
    }

    @Test
    void declaration_index_breaks_a_scope_tie() {
        assertBefore(key(0, 1, 3, 0, 9), key(0, 1, 3, 1, 0));
    }

    @Test
    void subscription_sequence_breaks_a_declaration_tie() {
        assertBefore(key(0, 1, 3, 2, 0), key(0, 1, 3, 2, 1));
        assertBefore(key(0, 1, 3, 2, 1), key(0, 1, 3, 2, 2));
    }

    @Test
    void equal_keys_compare_equal() {
        assertEquals(0, key(5, 1, 2, 3, 4).compareTo(key(5, 1, 2, 3, 4)));
    }

    @Test
    void the_anchors_are_ordered_and_leave_room_either_side() {
        assertTrue(Order.EARLY < Order.NORMAL);
        assertTrue(Order.NORMAL < Order.LATE);
        assertEquals(0, Order.NORMAL, "NORMAL is the default order");
        assertBefore(key(Order.EARLY - 1, 0, 0, 0, 0), key(Order.EARLY, 0, 0, 0, 0));
        assertBefore(key(Order.LATE, 0, 0, 0, 0), key(Order.LATE + 1, 0, 0, 0, 0));
    }

    @Test
    void sorting_gives_the_same_list_whatever_the_input_order() {
        // A Trinket on a jig registered later but ordered EARLY runs first; within one order, the
        // earlier jig and then the earlier scope run first.
        List<Key> expected = List.of(
                key(Order.EARLY, 2, 5, 0, 0),
                key(Order.NORMAL, 0, 1, 0, 0),
                key(Order.NORMAL, 0, 1, 1, 0),
                key(Order.NORMAL, 0, 7, 0, 0),
                key(Order.NORMAL, 1, 2, 0, 0),
                key(Order.NORMAL, 1, 2, 0, 3),
                key(Order.NORMAL, 1, 2, 0, 8),
                key(Order.LATE, 0, 0, 0, 0));

        for (long seed = 0; seed < 20; seed++) {
            List<Key> shuffled = new ArrayList<>(expected);
            Collections.shuffle(shuffled, new Random(seed));
            Collections.sort(shuffled);
            assertEquals(expected, shuffled, "seed " + seed);
        }
    }
}
