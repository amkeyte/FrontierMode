package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when an entity is about to join a level — wraps {@link
 * EntityJoinLevelEvent}. Not mob-specific: fires for any {@link Entity}, the same breadth the
 * wrapped Forge event has ("fired whenever an Entity joins a Level").
 *
 * <p>
 * {@link #cancel()} works as expected here — no gotcha like {@link MobSpawnFinalizeSignal}'s:
 * {@link EntityJoinLevelEvent} is plainly {@code @Cancelable}, and canceling it genuinely keeps
 * the entity from being added to the level (per that event's own doc).
 *
 * <p>
 * Posted once, unscoped, to the whole bus — every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerSpawnIngress.onEntityJoinLevel} (SAT_076). Unlike the other two signals
 * in this cluster, {@link EntityJoinLevelEvent} fires on <em>both</em> logical sides (its own
 * doc), so the producer explicitly filters to server-side only — this signal is server-only by
 * construction, not by the wrapped event's own nature.
 */
public final class EntityJoinLevelSignal extends SatchelSignal<EntityJoinLevelEvent> {

    private final Entity entity;
    private final Level level;
    private final boolean loadedFromDisk;

    public EntityJoinLevelSignal(EntityJoinLevelEvent forgeEvent) {
        super(forgeEvent);
        this.entity = Objects.requireNonNull(forgeEvent.getEntity(), "entity");
        this.level = Objects.requireNonNull(forgeEvent.getLevel(), "level");
        this.loadedFromDisk = forgeEvent.loadedFromDisk();
    }

    /** The entity about to join the level. */
    public Entity entity() {
        return entity;
    }

    /** The level the entity is joining. */
    public Level level() {
        return level;
    }

    /** Whether this entity was loaded from disk, as opposed to freshly created. */
    public boolean loadedFromDisk() {
        return loadedFromDisk;
    }
}
