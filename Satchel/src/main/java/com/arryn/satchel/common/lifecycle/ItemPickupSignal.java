package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a player is about to pick up an {@link ItemEntity} —
 * before the pickup completes, so a subscriber can {@link #cancel()} it to veto the pickup
 * outright (canceling {@link EntityItemPickupEvent} after the inventory-add already happened
 * wouldn't undo it; this signal always arrives before that point).
 *
 * <p>
 * {@code player} is deliberately typed as the side-agnostic {@link Player}, not {@code
 * ServerPlayer}, matching {@link MobDied}'s own common-code convention — {@link
 * EntityItemPickupEvent} only ever fires server-side in practice (see the producer's own doc),
 * but this signal's shape stays side-agnostic regardless; a consumer that specifically needs
 * {@code ServerPlayer} casts it itself.
 *
 * <p>
 * Posted once, unscoped, to the whole bus — every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onItemPickup} (SAT_072).
 */
public final class ItemPickupSignal extends SatchelSignal<EntityItemPickupEvent> {

    private final Player player;
    private final ItemEntity item;

    public ItemPickupSignal(EntityItemPickupEvent forgeEvent) {
        super(forgeEvent);
        this.player = Objects.requireNonNull(forgeEvent.getEntity(), "player");
        this.item = Objects.requireNonNull(forgeEvent.getItem(), "item");
    }

    /** The player attempting the pickup. */
    public Player player() {
        return player;
    }

    /** The item entity being picked up. */
    public ItemEntity item() {
        return item;
    }
}
