package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

/**
 * A Feral mob has died and is about to drop its loot: the module's event for Forge's
 * {@link LivingDropsEvent} (feral.md § No free loot). Its context is the mob. The ingress's resolver is
 * the Feral record test, so the death of any mob that does not carry the record never builds an event.
 * {@link FeralLootHandlers#onFeralDrops} is its one consumer.
 *
 * <p>Its veto is {@link #suppress()}. Valid only during dispatch: after the consumer returns, a read of
 * the native event throws.
 */
public final class FeralDrops extends ModuleSignal<LivingDropsEvent, Mob> {

    FeralDrops(LivingDropsEvent forgeEvent, Mob mob) {
        super(forgeEvent, mob);
    }

    /** The Feral mob that died. */
    public Mob mob() {
        return context();
    }

    /** Drops nothing: the native event is cancelled, so no loot item enters the world. */
    public void suppress() {
        nativeEvent().setCanceled(true);
    }
}
