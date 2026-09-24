package com.arryn.frontiermode.border.common.player;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.satchel.common.fixture.SatchelFixture;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Player-scoped facet owning the authoritative BorderPlayerStatus snapshot.
 * <p>
 * This facet is the sole authority that:
 * - accepts player status evaluation requests
 * - computes derived border context internally
 * - materializes BorderPlayerStatus
 * - tracks dirty state
 * <p>
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
     * <p>
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
        // RM_FRO_037: `current` (this fixture's own previous snapshot, or null before the first
        // tick) is threaded in so sicknessSeverity's target-and-catch-up climb has continuity to
        // climb from -- see BorderPlayerLogic.evaluate's own doc for why that state lives here
        // rather than on the stateless evaluator.
        BorderPlayerEval eval = logic.evaluate(borders, pos, current);

        return new BorderPlayerStatus(
                this,
                playerId,
                eval.nearestBorderId(),
                eval.distanceToNearest(),
                eval.insideNearest(),
                eval.relevantLayer(),
                eval.nearestLayer(),
                eval.frontierDistance(),
                eval.sicknessSeverity()
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

    /**
     * FRO_133 (playtest root cause, 2026-09-22): {@code sicknessSeverity}'s target-and-catch-up
     * climb carries forward from {@code current} tick to tick (see {@link #compute}'s own doc) --
     * death and respawn never reset it on their own, so a player who died deep in the Exterior
     * kept decaying down from whatever severity they died at, at {@code PlayerRules
     * .SICKNESS_CLIMB_RATE}'s own slow per-tick rate, regardless of their new (possibly safe)
     * respawn position -- re-triggering {@code BorderModule.applySicknessEffects} on every
     * refresh interval until it organically decayed below tier 1, which can take well over ten
     * seconds from a high starting severity. Clearing the active {@code MobEffectInstance}s on
     * respawn did nothing to this separate, underlying numeric state, which is why the debuff
     * kept reappearing after being cleared.
     *
     * <p>Clears {@code current} entirely rather than patching just the severity field -- the next
     * real tick recomputes everything fresh anyway (this fixture's own "not persisted, live-
     * recomputed" contract), and {@link BorderPlayerLogic#evaluate}'s own {@code previous == null}
     * branch already treats that as "start severity at 0," so this is the minimal change that
     * achieves the reset. A momentarily-null {@link #status()} between this call and the next
     * real tick is the same transient state every caller of it already handles (see
     * {@code BorderAPI.playerStatus}'s own "standby, don't crash" doc).
     */
    public void resetSicknessSeverity() {
        current = null;
    }
}
