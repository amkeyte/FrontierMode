package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.client.BossFlashClient;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import net.minecraft.world.level.Level;

/**
 * Event handlers for {@code BOSS_PUBLIC_JIG}: its client-side tick, which draws the boss's
 * close-range flash. {@code ScopeEvent.Tick} fires for every jig on the shared bus, so the jig key
 * is checked. The drawing itself lives in a client class that never loads on a dedicated server.
 */
final class BossPublicJigHandlers {

    private BossPublicJigHandlers() {
    }

    static void onTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        if (!FrontierKeys.BOSS_PUBLIC_JIG.equals(info.jigInfo().key)) return;
        Level level = ((LevelScope) info.scope()).level();
        if (!level.isClientSide) return;
        BossFlashClient.tick(level);
    }
}
