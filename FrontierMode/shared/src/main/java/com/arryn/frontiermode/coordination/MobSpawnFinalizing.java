package com.arryn.frontiermode.coordination;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

/**
 * A mob is about to be finalized: the module's event for the loader's finalize-spawn event, on both lines. Unscoped,
 * because a spawn has no scope to resolve to. {@link SpawnCoordinator} is its one consumer.
 *
 * <p>Each line's constructor runs for every spawn while the event has a subscriber, so it does only three reads. Valid
 * only during dispatch: the three copied facts stay readable.
 */
public interface MobSpawnFinalizing extends ModuleEvent<Void> {

    /** The mob about to be finalized. */
    Mob mob();

    /** The level it spawns into. */
    Level level();

    /** Why this spawn is happening (natural, spawner, event, etc.). */
    MobSpawnType spawnType();

    /**
     * Stops the mob from spawning, by setting the flag the loader enforces against the entity. There is no
     * {@code cancel()} on this event on purpose: cancelling the loader's finalize-spawn event only skips
     * {@code Mob#finalizeSpawn}'s initialization, and the entity still spawns.
     */
    void denySpawn();
}
