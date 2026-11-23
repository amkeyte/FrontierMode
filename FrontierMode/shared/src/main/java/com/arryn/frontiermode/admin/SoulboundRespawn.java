package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.player.Player;

/**
 * A player has respawned: the module's event for the loader's player-respawn event, on both lines. Its context is the
 * respawned player's host scope. The scope outlives the death, so the respawned player instance reaches the same stash
 * Trinket that took the tools out of the death drops.
 */
public interface SoulboundRespawn extends ModuleEvent<ScopeInfo> {

    /** The respawned player. */
    Player player();
}
