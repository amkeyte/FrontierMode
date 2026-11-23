package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

/**
 * A hostile mob is joining a level, fresh or loaded from disk: the module's event for the loader's entity-join event, on
 * both lines (feral.md § How Feral hooks the game). Its context is the mob. Each line's resolver passes only a mob whose
 * type {@link #passes}, so a non-mob entity and a passive mob are dropped before an event is built, and the ingress is
 * {@code SERVER}, so a join on the client's thread never reaches it. {@link FeralTagging#onHostileJoining} is its one
 * consumer.
 *
 * <p>It offers no veto. Valid only during dispatch.
 */
public interface HostileJoining extends ModuleEvent<Mob> {

    /**
     * The resolver's test, the cheap filter for a join, which fires for every entity: a {@code MobCategory.MONSTER} type.
     * {@code MobCategory.MONSTER}, not {@code instanceof Monster}: a Slime and a Magma Cube are real hostiles that do not
     * extend {@code Monster}.
     */
    static boolean passes(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER;
    }

    /** The hostile that is joining the level. */
    default Mob mob() {
        return context();
    }

    /** The level it is joining. */
    Level level();

    /** True for a mob rehydrated with its chunk, false for one that has just come into being. */
    boolean loadedFromDisk();
}
