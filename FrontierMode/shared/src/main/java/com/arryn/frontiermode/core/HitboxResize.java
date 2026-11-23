package com.arryn.frontiermode.core;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;

/**
 * An entity that a {@link ScaleSource} may scale is about to take a size: the module's event for the loader's
 * entity-size event, on both lines (Two Lines § Module events). Its context is the entity. The event fires from the
 * {@code Entity} constructor and from every {@code refreshDimensions()}, so each line's resolver is the cheap filter
 * ({@link EntityScales#canBeScaled}) and an event is built only for an entity a source may scale.
 *
 * <p>The loader's eye-height event is not subscribed: it is no longer fired, so the eye height rides on this event.
 */
public interface HitboxResize extends ModuleEvent<Entity> {

    /** The entity being sized. */
    default Entity entity() {
        return context();
    }

    /** The size the entity is about to take, before any scale. */
    EntityDimensions newSize();

    /** Replaces the size, and moves the eye height with it. */
    void resize(EntityDimensions size);
}
