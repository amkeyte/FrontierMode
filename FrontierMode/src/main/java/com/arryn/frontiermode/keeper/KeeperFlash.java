package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.keeper.client.BossFlashClient;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;

/**
 * The Keeper's close-range flash tick (wiki/frontiermode/architecture/keeper.md § Client-side eyes and flash): a
 * Trinket declared on {@code KEEPER_JIG}, which is {@code BOTH}, so it exists on each side and does nothing but on the
 * client. It keeps the client-only types out of its own body and delegates to {@link BossFlashClient}, which names
 * them, so it verifies on a dedicated server.
 */
final class KeeperFlash implements Ticking {

    @Override
    public void onTickAfter(ScopeInfo info) {
        if (!Satchel.isClient()) {
            return;
        }
        LevelScope scope = info.scopeAs();
        BossFlashClient.tick(scope.level());
    }
}
