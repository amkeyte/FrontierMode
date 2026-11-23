package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A player tosses an admin tool: the module's event for the loader's item-toss event, on both lines. Its context is the
 * tossing player, and each line's resolver passes only a stack that is an admin tool ({@link #passes}).
 */
public interface SoulboundToss extends ModuleEvent<Player> {

    /** The resolver's test: the stack is an admin tool. */
    static boolean passes(ItemStack stack) {
        return AdminTools.isAdminTool(stack);
    }

    /** The tossing player. */
    default Player player() {
        return context();
    }

    /** The tool being tossed. */
    ItemStack stack();

    /**
     * Cancels the toss. {@code Player#drop} has already taken the stack out of the inventory by the time the event fires,
     * so cancelling alone would delete it: the consumer returns it to the player.
     */
    void cancel();
}
