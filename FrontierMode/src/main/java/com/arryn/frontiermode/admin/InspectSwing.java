package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/**
 * A player holding the Inspect Stick swings at an entity: the module's event for Forge's
 * {@link AttackEntityEvent}. Its context is the swinging player, and the ingress's resolver passes only a
 * player whose main-hand stack carries Inspect, so every other swing in the game is dropped before an
 * event is built. It fires at the first line of {@code Player#attack}, before anything of the hit happens.
 */
public final class InspectSwing extends ModuleSignal<AttackEntityEvent, Player> {

    private final Entity target;

    InspectSwing(AttackEntityEvent forgeEvent, Player player) {
        super(forgeEvent, player);
        this.target = forgeEvent.getTarget();
    }

    /** The player holding the stick. */
    public Player player() {
        return context();
    }

    /** The entity swung at. */
    public Entity target() {
        return target;
    }

    /**
     * Stops the whole attack: no damage, knockback, aggro, sweep, durability or hunger, and no kill-credit
     * window.
     */
    public void stopAttack() {
        nativeEvent().setCanceled(true);
    }
}
