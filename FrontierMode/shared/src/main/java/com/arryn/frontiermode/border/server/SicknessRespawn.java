package com.arryn.frontiermode.border.server;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.player.Player;

/**
 * A player has respawned: the border module's event for the loader's player-respawn event, on both lines. Its
 * context is the respawned player's scope on {@code BORDER_PLAYER_JIG}, which outlives the death (it unloads
 * at logout only), so the respawn reaches the same {@code BorderPlayerTick} that ticked the player before it.
 * Heard there to clear Frontier Sickness as a fresh start.
 */
public interface SicknessRespawn extends ModuleEvent<ScopeInfo> {

    /** The respawned player. */
    Player player();
}
