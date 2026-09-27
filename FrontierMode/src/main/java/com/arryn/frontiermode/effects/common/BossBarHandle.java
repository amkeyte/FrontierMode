package com.arryn.frontiermode.effects.common;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * A boss bar at the top of the screen, shown to the players added to it -- the handle
 * {@link EffectsAPI#createBossBar} returns, so the vanilla {@link ServerBossEvent} is touched only
 * here. The bar keeps its own viewer set and sends its own packets. The caller decides who watches
 * and when it goes away; see effects.md § Boss bars.
 */
public final class BossBarHandle {

    private final ServerBossEvent event;

    BossBarHandle(ServerBossEvent event) {
        this.event = event;
    }

    /** Shows the bar to {@code player}; a no-op if they already see it. */
    public void addPlayer(ServerPlayer player) {
        event.addPlayer(player);
    }

    /**
     * Hides the bar from the player with this UUID; a no-op if they don't see it. By UUID so a
     * player who has disconnected can still be dropped from the viewer set.
     */
    public void removePlayer(UUID playerId) {
        for (ServerPlayer viewer : List.copyOf(event.getPlayers())) {
            if (viewer.getUUID().equals(playerId)) {
                event.removePlayer(viewer);
            }
        }
    }

    /** Fill from 0 to 1, clamped. Sent to viewers only when it changes. */
    public void setProgress(float progress) {
        event.setProgress(Math.max(0F, Math.min(1F, progress)));
    }

    /** Hides the bar from everyone. The handle can be reused afterward. */
    public void removeAll() {
        event.removeAllPlayers();
    }
}
