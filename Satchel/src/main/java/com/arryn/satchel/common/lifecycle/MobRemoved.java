package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/**
 * Posted to {@link SatchelEventBus} at the instant a mob that something has registered interest in
 * leaves its level, carrying <em>why</em> ({@link Entity.RemovalReason}) -- the one thing {@code
 * ScopeEvent.MobLostInterest} can't say. That signal fires from {@code MobJig}'s ~20-tick presence
 * poll and is reason-agnostic by design; this one fires from the real leave event, so the reason
 * is exact and needs no carrying between two moments. Both can fire for the same mob; neither
 * replaces the other.
 *
 * <p>
 * {@link #isGone()} and {@link #isUnloaded()} are the two-state view, deliberately <em>not</em>
 * vanilla's {@code RemovalReason#shouldDestroy()}: that is {@code false} for {@code
 * CHANGED_DIMENSION}, and for an interest-registered mob a dimension change is a departure that
 * ends this entity, so it counts as gone here:
 *
 * <table>
 *   <caption>Removal reason to state</caption>
 *   <tr><td>{@code KILLED}, {@code DISCARDED}, {@code CHANGED_DIMENSION}</td><td>gone</td></tr>
 *   <tr><td>{@code UNLOADED_TO_CHUNK}, {@code UNLOADED_WITH_PLAYER}</td><td>unloaded -- still exists</td></tr>
 * </table>
 *
 * Natural despawn and vanilla mob conversion (Zombie into Drowned) arrive as {@code DISCARDED},
 * so they read as gone.
 *
 * <p>
 * Not a {@link SatchelSignal}: nothing about a departure can be vetoed, same shape as {@link
 * MobDied}. Same gate as {@link MobDied} too -- posted only for a UUID some registered {@code
 * MobInterestRegistry} supplier currently has interest in, once, to the whole bus.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onEntityLeaveLevel} (SAT_084).
 */
public final class MobRemoved {

    private final Level level;
    private final UUID uuid;
    private final Entity entity;
    private final Entity.RemovalReason reason;

    public MobRemoved(Level level, UUID uuid, Entity entity, Entity.RemovalReason reason) {
        this.level = Objects.requireNonNull(level, "level");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** The level the mob left. */
    public Level level() {
        return level;
    }

    /** The mob's persistent UUID. */
    public UUID uuid() {
        return uuid;
    }

    /** The departing entity; it has already been marked removed. */
    public Entity entity() {
        return entity;
    }

    /** The raw reason, for a consumer that wants more than the two-state view. */
    public Entity.RemovalReason reason() {
        return reason;
    }

    /** The mob no longer exists (killed, discarded, or left for another dimension). */
    public boolean isGone() {
        return switch (reason) {
            case KILLED, DISCARDED, CHANGED_DIMENSION -> true;
            case UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER -> false;
        };
    }

    /** The mob was only unloaded with its chunk or player and still exists. */
    public boolean isUnloaded() {
        return !isGone();
    }
}
