package com.arryn.frontiermode.border.common.player;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.satchel.common.fixture.SatchelFixture;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Player-scoped facet owning the authoritative BorderPlayerStatus snapshot.
 *
 * This facet is the sole authority that:
 * - accepts player status evaluation requests
 * - computes derived border context internally
 * - materializes BorderPlayerStatus
 * - tracks dirty state
 *
 * FRO_026 rewrite: rebuilt against {@link SatchelFixture} (the current base class) instead of
 * the predecessor {@code SatchelSetting}, which no longer exists. Deliberately not persisted --
 * {@link BorderPlayerStatus}/{@link BorderPlayerEval}'s own docs already call this data
 * "not persisted or synced": it's a live-recomputed snapshot derived from world state
 * ({@code borders}, player position) each time {@link #accept} runs, not source-of-truth state
 * that needs to survive a restart. {@code markDirty()} is still called on real change so the
 * bundle's dirty-tracking stays accurate if a future consumer (sync, health-check) needs it.
 */
public final class BorderPlayerStatusFixture extends SatchelFixture {

    private BorderPlayerStatus current;

    private final BorderPlayerLogic logic = new BorderPlayerLogic();

    // ---------------------------------------------------------------------
    // Acceptance (authoritative choke point)
    // ---------------------------------------------------------------------

    /**
     * Accepts a player status evaluation request and updates authoritative
     * player border state if it has changed.
     *
     * The proposal carries identity only; all derived values are computed
     * internally by this facet.
     */
    public BorderPlayerStatus accept(
            BorderPlayerStatusProposal proposal,
            List<Border> borders,
            BlockPos pos
    ) {
        Objects.requireNonNull(proposal, "proposal");

        BorderPlayerStatus next =
                compute(proposal.playerId(), borders, pos);

        if (!Objects.equals(current, next)) {
            current = next;
            markDirty();
        }

        return current;
    }

    // ---------------------------------------------------------------------
    // Internal computation
    // ---------------------------------------------------------------------

    private BorderPlayerStatus compute(
            UUID playerId,
            List<Border> borders,
            BlockPos pos
    ) {
        BorderPlayerEval eval = logic.evaluate(borders, pos);

        return new BorderPlayerStatus(
                this,
                playerId,
                eval.nearestBorderId(),
                eval.distanceToNearest(),
                eval.insideNearest(),
                eval.layerIndex()
        );
    }

    // ---------------------------------------------------------------------
    // Read-only access
    // ---------------------------------------------------------------------

    /**
     * Returns the last authoritative player border status snapshot.
     */
    public BorderPlayerStatus status() {
        return current;
    }
}
