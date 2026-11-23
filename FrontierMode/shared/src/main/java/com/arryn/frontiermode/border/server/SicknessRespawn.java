package com.arryn.frontiermode.border.server;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * A player has respawned: the border module's event for Forge's {@link PlayerEvent.PlayerRespawnEvent}. Its
 * context is the respawned player's scope on {@code BORDER_PLAYER_JIG}, which outlives the death (it unloads
 * at logout only), so the respawn reaches the same {@code BorderPlayerTick} that ticked the player before it.
 * Heard there to clear Frontier Sickness as a fresh start.
 */
public final class SicknessRespawn extends ModuleSignal<PlayerEvent.PlayerRespawnEvent, ScopeInfo> {

    private final Player player;

    SicknessRespawn(PlayerEvent.PlayerRespawnEvent forgeEvent, ScopeInfo scope) {
        super(forgeEvent, scope);
        this.player = forgeEvent.getEntity();
    }

    /** The respawned player. */
    public Player player() {
        return player;
    }
}
