//package com.arryn.frontiermode.border.common.player;
//
//import com.arryn.frontiermode.border.common.level.Border;
//import com.arryn.satchel.facet.SatchelSetting;
//import net.minecraft.core.BlockPos;
//
//import java.util.List;
//import java.util.Objects;
//import java.util.UUID;
//
///**
// * Player-scoped facet owning the authoritative BorderPlayerStatus snapshot.
// *
// * This facet is the sole authority that:
// * - accepts player status evaluation requests
// * - computes derived border context internally
// * - materializes BorderPlayerStatus
// * - tracks dirty state
// */
//public final class BorderPlayerStatusFixture extends SatchelSetting {
//
//    private BorderPlayerStatus current;
//
//    private final BorderPlayerLogic logic = new BorderPlayerLogic();
//
//    // ---------------------------------------------------------------------
//    // Acceptance (authoritative choke point)
//    // ---------------------------------------------------------------------
//
//    /**
//     * Accepts a player status evaluation request and updates authoritative
//     * player border state if it has poll.
//     *
//     * The proposal carries identity only; all derived values are computed
//     * internally by this facet.
//     */
//    public BorderPlayerStatus accept(
//            BorderPlayerStatusProposal proposal,
//            List<Border> borders,
//            BlockPos pos
//    ) {
//        Objects.requireNonNull(proposal, "proposal");
//
//        BorderPlayerStatus next =
//                compute(proposal.playerId(), borders, pos);
//
//        if (!Objects.equals(current, next)) {
//            current = next;
//            markDirty();
//        }
//
//        return current;
//    }
//
//    // ---------------------------------------------------------------------
//    // Internal computation
//    // ---------------------------------------------------------------------
//
//    private BorderPlayerStatus compute(
//            UUID playerId,
//            List<Border> borders,
//            BlockPos pos
//    ) {
//        BorderPlayerEval eval = logic.evaluate(borders, pos);
//
//        return new BorderPlayerStatus(
//                this,
//                playerId,
//                eval.nearestBorderId(),
//                eval.distanceToNearest(),
//                eval.insideNearest(),
//                eval.layerIndex()
//        );
//    }
//
//    // ---------------------------------------------------------------------
//    // Read-only access
//    // ---------------------------------------------------------------------
//
//    /**
//     * Returns the last authoritative player border status snapshot.
//     */
//    public BorderPlayerStatus status() {
//        return current;
//    }
//}
