package com.arryn.frontiermode.core;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraftforge.event.entity.EntityEvent;

/**
 * An entity that a {@link ScaleSource} may scale is about to take a size: the module's event for Forge's
 * {@link EntityEvent.Size}, on both sides. Its context is the entity. The event fires from the {@code Entity}
 * constructor and from every {@code refreshDimensions()}, so the ingress's resolver is the cheap filter
 * ({@link EntityScales#canBeScaled}) and an event is built only for an entity a source may scale.
 *
 * <p>{@code EntityEvent.EyeHeight} is not subscribed: it is deprecated and no longer fired by Forge or vanilla, so the
 * eye height rides on this event.
 */
public final class HitboxResize extends ModuleSignal<EntityEvent.Size, Entity> {

    HitboxResize(EntityEvent.Size forgeEvent, Entity entity) {
        super(forgeEvent, entity);
    }

    /** The entity being sized. */
    public Entity entity() {
        return context();
    }

    /** The size the entity is about to take, before any scale. */
    public EntityDimensions newSize() {
        return nativeEvent().getNewSize();
    }

    /** Replaces the size, and moves the eye height with it. */
    public void resize(EntityDimensions size) {
        nativeEvent().setNewSize(size, true);
    }
}
