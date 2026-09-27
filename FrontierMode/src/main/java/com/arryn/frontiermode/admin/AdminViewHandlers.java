package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.admin.client.AdminViewClient;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import net.minecraft.world.level.Level;

/**
 * The debug view's client tick. {@code ScopeEvent.Tick} fires per active bundle on the shared bus for
 * every jig, and a jig with no bundle never ticks on the client, so this borrows the tick of
 * {@code GUARDIAN_PUBLIC_JIG} (a client-synced jig that always has its bundle) rather than owning a
 * tick source: the jig key is checked, and the work is client-side only. See {@link AdminViewClient}.
 */
final class AdminViewHandlers {

    private AdminViewHandlers() {
    }

    static void onTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        if (!FrontierKeys.GUARDIAN_PUBLIC_JIG.equals(info.jigInfo().key)) return;
        Level level = ((LevelScope) info.scope()).level();
        if (!level.isClientSide) return;
        AdminViewClient.tick(level);
    }
}
