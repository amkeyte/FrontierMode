package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;

/**
 * A Feral mob has died and is about to drop experience: the module's event for Forge's
 * {@link LivingExperienceDropEvent} (feral.md § No free loot). Its context is the mob. The ingress's
 * resolver is the Feral record test, so the death of any mob that does not carry the record never builds
 * an event. {@link FeralLootHandlers#onFeralExperience} is its one consumer.
 *
 * <p>Its veto is {@link #suppress()}. Valid only during dispatch: after the consumer returns, a read of
 * the native event throws.
 */
public final class FeralExperience extends ModuleSignal<LivingExperienceDropEvent, Mob> {

    FeralExperience(LivingExperienceDropEvent forgeEvent, Mob mob) {
        super(forgeEvent, mob);
    }

    /** The Feral mob that died. */
    public Mob mob() {
        return context();
    }

    /** Drops no experience: the native event is cancelled, so no orb is created. */
    public void suppress() {
        nativeEvent().setCanceled(true);
    }
}
