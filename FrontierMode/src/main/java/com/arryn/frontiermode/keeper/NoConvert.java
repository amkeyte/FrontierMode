package com.arryn.frontiermode.keeper;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.trinket.Trinket;

/**
 * A live Keeper never converts (wiki/frontiermode/architecture/keeper.md § A live Keeper never converts; owner's
 * ruling, SAT_101): a Zombie Keeper standing in water would otherwise become a Drowned that is no longer tracked as the
 * Keeper -- its record, size marker, health bar and loot all keyed to the old entity. Cancelling keeps it the Keeper;
 * ordinary mobs still convert. The Keeper kind subscribes it on the server. It hears {@link KeeperConversion}, a
 * module event scoped to the Keeper's mob scope. A conversion that skips Forge's Pre check (a Skeleton freezing into a
 * Stray) is not covered.
 */
final class NoConvert implements Trinket {

    @Override
    public void onAttach(ScopeInfo info) {
        info.listenFor(KeeperConversion.class, this::onConversion);
    }

    private void onConversion(KeeperConversion event) {
        event.cancel();
    }
}
