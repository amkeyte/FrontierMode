package com.arryn.frontiermode.bosstell;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

/**
 * A player is about to pick up an Amethyst Compass: the module's event for Forge's
 * {@link EntityItemPickupEvent}. Its context is the picking player. The ingress's resolver passes only a
 * stack of an {@code AmethystCompassItem}, so every other pickup is dropped before an event is built.
 * {@link BossTellIngress} holds its one consumer.
 *
 * <p>Valid only during dispatch: after the consumer returns, a read of the native event throws, and the
 * stack taken at construction stays readable.
 */
public final class CompassPickup extends ModuleSignal<EntityItemPickupEvent, Player> {

    private final ItemStack stack;

    CompassPickup(EntityItemPickupEvent forgeEvent, Player picker) {
        super(forgeEvent, picker);
        this.stack = forgeEvent.getItem().getItem();
    }

    /** The player picking the compass up. */
    public Player picker() {
        return context();
    }

    /** The compass stack on the ground. */
    public ItemStack stack() {
        return stack;
    }

    /**
     * Cancels the pickup. The compass stays exactly where it is, still pickable on a later attempt;
     * nothing is removed or destroyed.
     */
    public void blockPickup() {
        nativeEvent().setCanceled(true);
    }
}
