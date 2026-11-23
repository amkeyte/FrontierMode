package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * A Sentinel is about to take damage, and the amount can still be changed: the Sentinel module's event for the loader's
 * damage event at the stage after armor and magic reduction (Two Lines § Damage stages on each line), server only
 * (sentinels.md § Sentinels answer armor too). Its context is the boss id of the Sentinel's Keeper. The loader's event
 * fires for every entity that takes damage, so each line's resolver asks {@link #bossIdOf}, which answers only for a mob
 * that carries the Sentinel record. The one consumer divides the amount by the Keeper's armor multiplier while that is
 * in force.
 *
 * <p>{@link #amount()} reads the live event. Valid only during dispatch.
 */
public interface SentinelTakingDamage extends ModuleEvent<UUID> {

    /** The resolver's test: the Keeper's boss id for a mob that carries the Sentinel record, empty for anything else. */
    static Optional<UUID> bossIdOf(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return Optional.empty();
        }
        return SentinelData.read(mob).map(SentinelData.Facts::bossId);
    }

    /** The boss id of the Sentinel's Keeper. */
    default UUID bossId() {
        return context();
    }

    /** The level the Sentinel is in. */
    Level level();

    /** The damage about to come off, after armor. Live. */
    float amount();

    void setAmount(float amount);
}
