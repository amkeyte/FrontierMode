package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A player is about to pick up an Amethyst Compass: the module's event for the loader's item-pickup event, on both lines.
 * Its context is the picking player. Each line's resolver passes only a stack that {@link #passes}, so every other pickup
 * is dropped before an event is built. {@link BossTellConsumers} holds its one consumer.
 *
 * <p>Valid only during dispatch: after the consumer returns, a read of the native event throws, and the stack taken at
 * construction stays readable.
 */
public interface CompassPickup extends ModuleEvent<Player> {

    /** The resolver's test: a pickup pays one event only for a stack of an {@code AmethystCompassItem}. */
    static boolean passes(ItemStack stack) {
        return stack.getItem() instanceof AmethystCompassItem;
    }

    /** The player picking the compass up. */
    default Player picker() {
        return context();
    }

    /** The compass stack on the ground. */
    ItemStack stack();

    /**
     * Blocks the pickup. The compass stays exactly where it is, still pickable on a later attempt; nothing is removed or
     * destroyed.
     */
    void blockPickup();
}
