package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A player holding the Inspect Stick swings at an entity: the module's event for the loader's attack-entity event, on
 * both lines. Its context is the swinging player, and each line's resolver passes only a player whose main-hand stack
 * {@link #passes}, so every other swing in the game is dropped before an event is built. It fires at the first line of
 * {@code Player#attack}, before anything of the hit happens.
 */
public interface InspectSwing extends ModuleEvent<Player> {

    /** The resolver's test: the main-hand stack carries Inspect. */
    static boolean passes(ItemStack mainHand) {
        return StackData.enchantmentLevel(mainHand, AdminToolsRegisters.INSPECT) > 0;
    }

    /** The player holding the stick. */
    default Player player() {
        return context();
    }

    /** The entity swung at. */
    Entity target();

    /** Stops the whole attack: no damage, knockback, aggro, sweep, durability or hunger, and no kill-credit window. */
    void stopAttack();
}
