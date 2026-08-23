package test.arryn.satchel.jig.mob;

import com.arryn.satchel.common.jig.mob.MobReconcileLogic;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * First jig/scope-layer test in the module (RM_SAT_021, "Frank") -- deliberately minimal,
 * mirroring {@code TickThrottlerTest}'s plain-JUnit, no-mocking style. Covers only the one
 * genuinely correctness-sensitive, side-effect-free piece of {@code MobJig.reconcile}: the
 * teardown set computation. Introduction has no equivalent pure-logic branch worth testing here
 * -- it's a thin wrapper over {@code LogicalFoundation.introduceSource}, which is already
 * idempotent by construction.
 */
class MobReconcileLogicTest {

    private static UUID uuid(int seed) {
        return new UUID(0L, seed);
    }

    @Test
    void everything_still_resolving_tears_nothing_down() {
        Set<UUID> resolved = Set.of(uuid(1), uuid(2));
        Set<UUID> scoped = Set.of(uuid(1), uuid(2));

        assertEquals(Set.of(), MobReconcileLogic.computeTeardowns(resolved, scoped));
    }

    @Test
    void a_scoped_uuid_missing_from_resolved_is_torn_down() {
        // Standing in for EITHER a chunk unload OR a genuine removal -- the same input either
        // way, which is exactly what proves the reason-agnostic contract at the logic level:
        // this method cannot tell (and must not try to tell) the two apart.
        Set<UUID> resolved = Set.of(uuid(1));
        Set<UUID> scoped = Set.of(uuid(1), uuid(2));

        assertEquals(Set.of(uuid(2)), MobReconcileLogic.computeTeardowns(resolved, scoped));
    }

    @Test
    void nothing_scoped_tears_nothing_down_regardless_of_resolved() {
        Set<UUID> resolved = Set.of(uuid(1), uuid(2));
        Set<UUID> scoped = Set.of();

        assertEquals(Set.of(), MobReconcileLogic.computeTeardowns(resolved, scoped));
    }

    @Test
    void a_resolved_uuid_not_yet_scoped_is_not_a_teardown() {
        // Introduction is a separate, already-idempotent path -- this method only ever computes
        // removals, never additions.
        Set<UUID> resolved = Set.of(uuid(1), uuid(3));
        Set<UUID> scoped = Set.of(uuid(1));

        assertEquals(Set.of(), MobReconcileLogic.computeTeardowns(resolved, scoped));
    }
}
