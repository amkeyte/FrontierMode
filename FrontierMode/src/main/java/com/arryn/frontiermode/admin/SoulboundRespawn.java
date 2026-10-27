package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * A player has respawned: the module's event for Forge's {@link PlayerEvent.PlayerRespawnEvent}. Its
 * context is the respawned player's host scope. The scope outlives the death, so the respawned player
 * instance reaches the same stash Trinket that took the tools out of the death drops.
 */
public final class SoulboundRespawn extends ModuleSignal<PlayerEvent.PlayerRespawnEvent, ScopeInfo> {

    private final Player player;

    SoulboundRespawn(PlayerEvent.PlayerRespawnEvent forgeEvent, ScopeInfo scope) {
        super(forgeEvent, scope);
        this.player = forgeEvent.getEntity();
    }

    /** The respawned player. */
    public Player player() {
        return player;
    }
}
