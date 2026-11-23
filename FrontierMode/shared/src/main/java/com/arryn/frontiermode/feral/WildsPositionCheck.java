package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

/**
 * The position gate of a spawn, after the mob exists and has been placed: the module's event for the loader's
 * position-check event, on both lines (feral.md § How Feral hooks the game). Its context is the mob. Each line's
 * resolver passes only a mob that {@link #passes}, so every other firing is dropped before an event is built.
 * {@link FeralSpawnGateHandlers#onPositionCheck} is its one consumer.
 *
 * <p>The event carries a result: {@link #allow()} forces the position through, bypassing both the light test and the
 * obstruction test, {@link #deny()} stops it, and neither leaves vanilla's answer. A deny is sticky. Valid only during
 * dispatch.
 */
public interface WildsPositionCheck extends ModuleEvent<Mob> {

    /** The resolver's test: a {@code MobCategory.MONSTER} mob standing in the Wilds. */
    static boolean passes(Mob mob) {
        //noinspection resource
        return mob.getType().getCategory() == MobCategory.MONSTER
                && BorderAPI.frontierDistanceAt(mob.level(), mob.blockPosition()) > 0;
    }

    /** The mob whose position is being checked; it already exists and has been placed. */
    default Mob mob() {
        return context();
    }

    /** The level the spawn is happening in. */
    default Level level() {
        return mob().level();
    }

    ModuleSignal.Verdict verdict();

    void allow();

    void deny();
}
