package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;

/**
 * A player tosses an admin tool: the module's event for Forge's {@link ItemTossEvent}. Its context is the
 * tossing player, and the ingress's resolver passes only a stack that is an admin tool.
 */
public final class SoulboundToss extends ModuleSignal<ItemTossEvent, Player> {

    private final ItemStack stack;

    SoulboundToss(ItemTossEvent forgeEvent, Player player) {
        super(forgeEvent, player);
        this.stack = forgeEvent.getEntity().getItem();
    }

    /** The tossing player. */
    public Player player() {
        return context();
    }

    /** The tool being tossed. */
    public ItemStack stack() {
        return stack;
    }

    /**
     * Cancels the toss. {@code Player#drop} has already taken the stack out of the inventory by the time
     * the event fires, so cancelling alone would delete it: the consumer returns it to the player.
     */
    public void cancel() {
        nativeEvent().setCanceled(true);
    }
}
