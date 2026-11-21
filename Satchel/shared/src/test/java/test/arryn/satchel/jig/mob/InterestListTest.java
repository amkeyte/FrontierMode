package test.arryn.satchel.jig.mob;

import com.arryn.satchel.common.jig.mob.InterestList;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mob jig's interest list (SAT_124): add and remove are idempotent and immediate, the cap check
 * counts only new entries, a snapshot survives changes to the list, and the level hint is kept only
 * for a listed UUID. Plain JUnit; the level type is a parameter.
 */
class InterestListTest {

    private static UUID uuid(int seed) {
        return new UUID(0L, seed);
    }

    @Test
    void add_reports_whether_the_uuid_was_new_and_is_idempotent() {
        InterestList<String> list = new InterestList<>();

        assertTrue(list.add(uuid(1)));
        assertFalse(list.add(uuid(1)));
        assertEquals(1, list.size());
        assertTrue(list.contains(uuid(1)));
    }

    @Test
    void remove_takes_effect_at_once_and_is_idempotent() {
        InterestList<String> list = new InterestList<>();
        list.add(uuid(1));

        assertTrue(list.remove(uuid(1)));
        assertFalse(list.contains(uuid(1)));
        assertFalse(list.remove(uuid(1)));
        assertEquals(0, list.size());
    }

    @Test
    void contains_is_false_for_null_rather_than_throwing() {
        assertFalse(new InterestList<String>().contains(null));
    }

    @Test
    void a_snapshot_is_unaffected_by_later_changes_and_lets_the_walk_remove_entries() {
        InterestList<String> list = new InterestList<>();
        list.add(uuid(1));
        list.add(uuid(2));
        list.add(uuid(3));

        List<UUID> snapshot = list.snapshot();
        for (UUID each : snapshot) {
            list.remove(each);   // lostInterest inside the walk: safe
        }
        list.add(uuid(4));

        assertEquals(3, snapshot.size());
        assertEquals(1, list.size());
        assertTrue(list.contains(uuid(4)));
    }

    @Test
    void the_cap_counts_only_a_new_entry_against_a_full_list() {
        InterestList<String> list = new InterestList<>();
        list.add(uuid(1));
        list.add(uuid(2));

        assertTrue(list.wouldExceed(uuid(3), 2));      // new, and the list is full
        assertFalse(list.wouldExceed(uuid(1), 2));     // already listed: not an addition
        assertFalse(list.wouldExceed(uuid(3), 3));     // room for it
    }

    @Test
    void the_level_hint_is_kept_for_a_listed_uuid_only() {
        InterestList<String> list = new InterestList<>();
        list.add(uuid(1));

        assertNull(list.hint(uuid(1)));
        list.setHint(uuid(1), "overworld");
        assertEquals("overworld", list.hint(uuid(1)));

        list.setHint(uuid(9), "nether");               // not listed: ignored
        assertNull(list.hint(uuid(9)));

        list.remove(uuid(1));
        assertNull(list.hint(uuid(1)));
    }

    @Test
    void a_sample_is_bounded_and_clear_empties_the_list() {
        InterestList<String> list = new InterestList<>();
        for (int i = 0; i < 20; i++) list.add(uuid(i));

        assertEquals(5, list.sample(5).size());
        assertEquals(20, list.sample(50).size());

        list.clear();
        assertEquals(0, list.size());
        assertEquals(List.of(), list.snapshot());
    }
}
