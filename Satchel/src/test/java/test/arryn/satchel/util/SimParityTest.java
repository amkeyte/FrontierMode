package test.arryn.satchel.util;

import com.arryn.satchel.common.util.SimParity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimParityTest {

    /* ------------------------------------------------------------
     * isCheckpoint()
     * --------------------------------------------------------- */

    @Test
    void interval_must_be_positive() {
        assertThrows(IllegalArgumentException.class,
                () -> SimParity.isCheckpoint(100, 0));
        assertThrows(IllegalArgumentException.class,
                () -> SimParity.isCheckpoint(100, -5));
    }

    @Test
    void checkpoint_gates_on_gameTime_mod_interval() {
        assertTrue(SimParity.isCheckpoint(0, 20));
        assertTrue(SimParity.isCheckpoint(20, 20));
        assertTrue(SimParity.isCheckpoint(40, 20));
        assertFalse(SimParity.isCheckpoint(1, 20));
        assertFalse(SimParity.isCheckpoint(19, 20));
        assertFalse(SimParity.isCheckpoint(21, 20));
    }

    @Test
    void checkpoint_self_corrects_after_a_missed_tick() {
        // Simulates a client that stalls and skips a tick -- it re-agrees with the server
        // at the next multiple of the interval without needing an explicit resync.
        long interval = 20;
        assertFalse(SimParity.isCheckpoint(39, interval)); // stalled, missed tick 40's neighbor
        assertTrue(SimParity.isCheckpoint(40, interval));  // caught up, re-agrees automatically
    }

    /* ------------------------------------------------------------
     * parityValue() / parityRoll() -- determinism and sensitivity
     * --------------------------------------------------------- */

    @Test
    void null_id_is_rejected() {
        assertThrows(NullPointerException.class,
                () -> SimParity.parityValue(null, 100, 0));
    }

    @Test
    void same_inputs_produce_the_same_value_every_time() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");

        long first = SimParity.parityValue(id, 1200, 42);
        long second = SimParity.parityValue(id, 1200, 42);

        assertEquals(first, second);
    }

    @Test
    void different_salts_diverge() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");

        long a = SimParity.parityValue(id, 1200, 42);
        long b = SimParity.parityValue(id, 1200, 43);

        assertNotEquals(a, b);
    }

    @Test
    void different_gameTimes_diverge() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");

        long a = SimParity.parityValue(id, 1200, 42);
        long b = SimParity.parityValue(id, 1220, 42);

        assertNotEquals(a, b);
    }

    @Test
    void different_ids_diverge() {
        long a = SimParity.parityValue(
                UUID.fromString("11111111-2222-3333-4444-555555555555"), 1200, 42);
        long b = SimParity.parityValue(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), 1200, 42);

        assertNotEquals(a, b);
    }

    @Test
    void parityRoll_stays_within_zero_inclusive_one_exclusive() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");

        for (long t = 0; t < 500; t += 20) {
            double roll = SimParity.parityRoll(id, t, 7);
            assertTrue(roll >= 0.0 && roll < 1.0, "roll out of range: " + roll);
        }
    }

    /* ------------------------------------------------------------
     * Golden vectors -- locks the mix function's exact output so a future
     * refactor can't silently change it without this test catching it.
     * (Independently computed, not derived from the implementation under test.)
     * --------------------------------------------------------- */

    @Test
    void golden_vector_one() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertEquals(-5375357452913312860L, SimParity.parityValue(id, 1200, 42));
        assertEquals(0.7086012885832619, SimParity.parityRoll(id, 1200, 42));
    }

    @Test
    void golden_vector_two() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(8199580975773293796L, SimParity.parityValue(id, 0, 0));
        assertEquals(0.4445001753702108, SimParity.parityRoll(id, 0, 0));
    }

    @Test
    void golden_vector_three_different_salt() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertEquals(6639795784077522504L, SimParity.parityValue(id, 1200, 43));
        assertEquals(0.3599440506978471, SimParity.parityRoll(id, 1200, 43));
    }

    @Test
    void golden_vector_four_different_gameTime() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertEquals(1301226041932218430L, SimParity.parityValue(id, 1220, 42));
        assertEquals(0.07053960507788126, SimParity.parityRoll(id, 1220, 42));
    }
}
