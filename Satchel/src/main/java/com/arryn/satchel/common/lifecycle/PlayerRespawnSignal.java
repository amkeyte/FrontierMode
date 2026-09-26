package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} after a player respawns -- death, or returning from the End --
 * wrapping {@link PlayerEvent.PlayerRespawnEvent}. Respawn replaces the player's server-side
 * entity instance, so {@link #player()} is the <em>new</em> instance; anything a subscriber cached
 * about the old one is stale.
 *
 * <p>
 * Pure notification: {@link PlayerEvent.PlayerRespawnEvent} is neither {@code @Cancelable} nor
 * {@code @HasResult}, so {@link #cancel()}/{@link #allow()}/{@link #deny()} throw
 * {@code UnsupportedOperationException} on this signal.
 *
 * <p>
 * Posted once, unscoped, to the whole bus -- every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onPlayerRespawn} (SAT_083). Server-only: the event is fired
 * from {@code PlayerList#respawn}.
 */
public final class PlayerRespawnSignal extends SatchelSignal<PlayerEvent.PlayerRespawnEvent> {

    private final Player player;
    private final boolean endConquered;

    public PlayerRespawnSignal(PlayerEvent.PlayerRespawnEvent forgeEvent) {
        super(forgeEvent);
        this.player = Objects.requireNonNull(forgeEvent.getEntity(), "player");
        this.endConquered = forgeEvent.isEndConquered();
    }

    /** The respawned player -- the new entity instance, not the one that died. */
    public Player player() {
        return player;
    }

    /** Whether this respawn is the return from conquering the End, rather than a death. */
    public boolean endConquered() {
        return endConquered;
    }
}
