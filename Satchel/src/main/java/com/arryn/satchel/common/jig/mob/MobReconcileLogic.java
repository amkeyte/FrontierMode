package com.arryn.satchel.common.jig.mob;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure logic extracted from {@link MobJig#reconcile} so it's testable without a Minecraft/Forge
 * bootstrap. The only genuinely correctness-sensitive, side-effect-free piece of the
 * reconciliation cycle: given what resolved this cycle and what was already scoped, which UUIDs
 * must be torn down. Introduction has no equivalent pure-logic branch worth extracting --
 * {@code LogicalFoundation.introduceSource} is already idempotent on its own.
 *
 * <p>
 * Deliberately reason-agnostic: a UUID missing from {@code resolvedThisCycle} might be a chunk
 * unload or a genuine removal (death, discard) -- this method cannot tell the difference, and per
 * MobJig's design, must not try to.
 */
public final class MobReconcileLogic {

    private MobReconcileLogic() {}

    public static Set<UUID> computeTeardowns(
            Set<UUID> resolvedThisCycle,
            Set<UUID> currentlyScoped
    ) {
        Set<UUID> teardowns = new HashSet<>(currentlyScoped);
        teardowns.removeAll(resolvedThisCycle);
        return teardowns;
    }
}
