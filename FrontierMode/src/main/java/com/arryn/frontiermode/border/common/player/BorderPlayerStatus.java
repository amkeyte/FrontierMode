//package com.arryn.frontiermode.border.common.player;
//
//import java.util.UUID;
//
///**
// * Authoritative snapshot of border-related state for a player.
// *
// * Constructed only by BorderPlayerStatusFixture.
// */
//public final class BorderPlayerStatus {
//
//    private final BorderPlayerStatusFixture authority;
//
//    private final UUID playerId;
//    private final UUID nearestBorderId;
//    private final int distanceToNearest;
//    private final boolean insideNearest;
//    private final int layerIndex;
//
//    BorderPlayerStatus(
//            BorderPlayerStatusFixture authority,
//            UUID playerId,
//            UUID nearestBorderId,
//            int distanceToNearest,
//            boolean insideNearest,
//            int layerIndex
//    ) {
//        this.authority = authority;
//        this.playerId = playerId;
//        this.nearestBorderId = nearestBorderId;
//        this.distanceToNearest = distanceToNearest;
//        this.insideNearest = insideNearest;
//        this.layerIndex = layerIndex;
//    }
//
//    // ----------------------------
//    // Identity / authority
//    // ----------------------------
//
//    public UUID playerId() {
//        return playerId;
//    }
//
//    BorderPlayerStatusFixture authority() {
//        return authority;
//    }
//
//    // ----------------------------
//    // Read-only state
//    // ----------------------------
//
//    public UUID nearestBorderId() {
//        return nearestBorderId;
//    }
//
//    public int distanceToNearest() {
//        return distanceToNearest;
//    }
//
//    public boolean insideNearest() {
//        return insideNearest;
//    }
//
//    public int layerIndex() {
//        return layerIndex;
//    }
//}
