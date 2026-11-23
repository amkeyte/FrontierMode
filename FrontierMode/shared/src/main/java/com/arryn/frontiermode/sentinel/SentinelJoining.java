package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * A mob that carries the Sentinel record is joining a level, fresh or loaded from disk: the Sentinel module's event for
 * the loader's entity-join event, on both lines (sentinels.md § Presence). Its context is the mob. Each line's resolver
 * passes only a mob {@link #passes}, so every other entity is dropped before an event is built, and the ingress is
 * {@code SERVER}, so a join on the client's thread never reaches it. {@link SentinelJigHandlers#onSentinelJoining} is its
 * one consumer.
 *
 * <p>It offers no veto. Valid only during dispatch.
 */
public interface SentinelJoining extends ModuleEvent<Mob> {

    /** The resolver's test, the cheap filter for a join, which fires for every entity: a Sentinel candidate that carries the record. */
    static boolean passes(Mob mob) {
        return SentinelMarkers.isCandidate(mob) && SentinelData.has(mob);
    }

    /** The Sentinel that is joining the level. */
    default Mob mob() {
        return context();
    }

    /** The level it is joining. */
    Level level();

    /** True for a mob rehydrated with its chunk, false for one that has just come into being. */
    boolean loadedFromDisk();
}
