package com.arryn.frontiermode.core;

import net.minecraft.world.entity.Entity;

/**
 * What a module hands {@link EntityScales} to say how big its entities are (wiki/frontiermode/architecture/core.md §
 * Scale sources). A boss and a Sentinel are each drawn and hit at a scale their module knows and {@code core} does
 * not. A source is stateless and side-agnostic: it asks the entity which level it is in, answers from its module's
 * own lookup, keeps no world data, and tolerates a null level, because {@code EntityEvent.Size} fires from the
 * {@code Entity} constructor.
 */
public interface ScaleSource {

    /** The cheap gate: could this entity be mine? Called on hot paths, so it rejects before any lookup. */
    boolean mayScale(Entity entity);

    /** The entity's scale, or {@code 1.0F} when it is not mine. */
    float scaleOf(Entity entity);
}
