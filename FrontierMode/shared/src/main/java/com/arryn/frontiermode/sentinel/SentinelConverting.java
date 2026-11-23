package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.LivingEntity;

/**
 * A mob that carries the Sentinel record is about to convert into another type: the Sentinel module's event for the
 * loader's pre-conversion event, on both lines (sentinels.md § Presence). Its context is the mob. Each line's resolver
 * passes only a mob that {@link #passes}, so every other conversion is dropped before an event is built.
 * {@link SentinelJigHandlers#onSentinelConverting} is its one consumer.
 *
 * <p>{@link #cancel()} keeps the mob as it is. Valid only during dispatch.
 */
public interface SentinelConverting extends ModuleEvent<LivingEntity> {

    /** The resolver's test: the mob carries the Sentinel record. */
    static boolean passes(LivingEntity mob) {
        return SentinelData.has(mob);
    }

    /** The Sentinel that would convert. */
    default LivingEntity mob() {
        return context();
    }

    /** Stops the conversion. */
    void cancel();
}
