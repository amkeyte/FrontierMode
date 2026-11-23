package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.trinket.Trinket;

/**
 * Allies never attack a Keeper (wiki/frontiermode/architecture/keeper.md § Allies never attack a Keeper): an
 * iron golem or a tamed wolf never takes a boss as its target, even when its owner is fighting it. The Keeper kind
 * subscribes it on the server. It hears {@link KeeperTargetChange}, a module event scoped to the Keeper's mob scope,
 * so it is built only when the new target is a boss; cancelling keeps the ally's current target, which covers a golem's
 * own scan, being hit, and a wolf following its owner. The credit rule is the backstop for help this doesn't reach.
 */
final class AllyTruce implements Trinket {

    @Override
    public void onAttach(ScopeInfo info) {
        info.listenFor(KeeperTargetChange.class, this::onTargetChange);
    }

    private void onTargetChange(KeeperTargetChange event) {
        if (DifficultyAPI.isAlly(event.changingMob())) {
            event.cancel();
        }
    }
}
