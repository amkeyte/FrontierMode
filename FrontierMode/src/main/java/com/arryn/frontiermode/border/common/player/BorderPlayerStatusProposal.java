//package com.arryn.frontiermode.border.common.player;
//
//import java.util.Objects;
//import java.util.UUID;
//
///**
// * Player status evaluation request.
// *
// * This proposal intentionally carries NO derived border data.
// * It exists only to anchor evaluation to a player identity and
// * to provide a stable mutation seam for future player-authored
// * status inputs.
// */
//public final class BorderPlayerStatusProposal {
//
//    private final UUID playerId;
//
//    public BorderPlayerStatusProposal(UUID playerId) {
//        this.playerId = Objects.requireNonNull(playerId, "playerId");
//    }
//
//    UUID playerId() {
//        return playerId;
//    }
//}
