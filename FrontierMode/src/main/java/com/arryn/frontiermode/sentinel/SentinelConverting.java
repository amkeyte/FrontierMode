package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingConversionEvent;

import java.util.Optional;

/**
 * A mob that carries the Sentinel record is about to convert into another type: the Sentinel module's event for
 * Forge's {@link LivingConversionEvent.Pre} (sentinels.md § Presence). Its context is the mob. The ingress's resolver
 * passes only a mob that carries the record, so every other conversion is dropped before an event is built.
 * {@link SentinelJigHandlers#onSentinelConverting} is its one consumer.
 *
 * <p>{@link #cancel()} keeps the mob as it is. Valid only during dispatch: after the consumer returns, a read of the
 * native event throws.
 */
public final class SentinelConverting extends ModuleSignal<LivingConversionEvent.Pre, LivingEntity> {

    /** The Forge event this module event reflects, for the ingress's translate. */
    static final Class<LivingConversionEvent.Pre> NATIVE = LivingConversionEvent.Pre.class;

    SentinelConverting(LivingConversionEvent.Pre forgeEvent, LivingEntity mob) {
        super(forgeEvent, mob);
    }

    /** The translate's resolver: the mob for one that carries the Sentinel record, empty for any other. */
    static Optional<LivingEntity> resolve(LivingConversionEvent.Pre event) {
        return SentinelData.has(event.getEntity()) ? Optional.of(event.getEntity()) : Optional.empty();
    }

    /** The Sentinel that would convert. */
    public LivingEntity mob() {
        return context();
    }

    /** Stops the conversion. */
    public void cancel() {
        nativeEvent().setCanceled(true);
    }
}
