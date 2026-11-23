package com.arryn.frontiermode.difficulty;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * A mob is joining a level for the first time: the module's event for the loader's entity-join event, on both lines
 * (difficulty.md § When it's set). Its context is the mob. Each line's resolver passes only a fresh join of a
 * {@link Mob} ({@link #passes}), so a chunk reload or a non-mob entity is dropped before an event is built, and the
 * ingress is {@code SERVER}, so a join on the client's thread never reaches it. {@link DifficultyHandlers#onMobJoining}
 * is its one consumer.
 *
 * <p>It offers no veto. Valid only during dispatch.
 */
public interface MobJoining extends ModuleEvent<Mob> {

    /**
     * The resolver's test, the cheap filter for a join, which fires for every entity: a join that is not a reload with
     * its chunk (a mob reloaded with its chunk already has its values).
     */
    static boolean passes(boolean loadedFromDisk) {
        return !loadedFromDisk;
    }

    /** The mob that is joining the level. */
    default Mob mob() {
        return context();
    }

    /** The level it is joining. */
    Level level();
}
