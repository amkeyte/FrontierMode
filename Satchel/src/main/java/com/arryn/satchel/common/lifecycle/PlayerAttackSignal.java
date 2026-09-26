package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a player attacks an entity, wrapping {@link
 * AttackEntityEvent} -- the first thing {@code Player#attack} does, before any of the hit applies.
 * A subscriber that {@link #cancel()}s it stops the whole attack: no damage, knockback, aggro,
 * sweep, durability, exhaustion, or kill-credit window.
 *
 * <p>
 * {@link #target()} is any {@link Entity}, not just a living one -- armor stands, item frames,
 * boats, and end crystals are attacked through the same path -- so a subscriber filters for the
 * targets it cares about. {@link AttackEntityEvent} is {@code @Cancelable} but has no {@code
 * Result}, so {@link #allow()}/{@link #deny()} throw {@code UnsupportedOperationException} on this
 * signal.
 *
 * <p>
 * Server-side only. The event also fires client-side, for the local player's predicted attack;
 * that firing is dropped at ingress, so a server-side cancel does not stop the client's own swing
 * animation -- cosmetic, and nothing of the hit applies. A spectator's attack never reaches {@code
 * Player#attack} on the server ({@code ServerPlayer#attack} spectates the target instead), so it
 * never posts this signal.
 *
 * <p>
 * {@code player} is typed as the side-agnostic {@link Player}, matching {@link MobDied}'s own
 * common-code convention; a consumer that needs {@code ServerPlayer} casts it itself.
 *
 * <p>
 * Posted once, unscoped, to the whole bus -- every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onPlayerAttack} (SAT_087).
 */
public final class PlayerAttackSignal extends SatchelSignal<AttackEntityEvent> {

    private final Player player;
    private final Entity target;

    public PlayerAttackSignal(AttackEntityEvent forgeEvent) {
        super(forgeEvent);
        this.player = Objects.requireNonNull(forgeEvent.getEntity(), "player");
        this.target = Objects.requireNonNull(forgeEvent.getTarget(), "target");
    }

    /** The attacking player. */
    public Player player() {
        return player;
    }

    /** The entity being attacked -- any entity, not only a living one. */
    public Entity target() {
        return target;
    }
}
