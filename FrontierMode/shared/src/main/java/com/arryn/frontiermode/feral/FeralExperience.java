package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.Mob;

/**
 * A Feral mob has died and is about to drop experience: the module's event for the loader's experience-drop event, on
 * both lines (feral.md § No free loot). Its context is the mob. Each line's resolver asks {@link #passes}, so the death
 * of any mob that does not carry the Feral record never builds an event. {@link FeralLootHandlers#onFeralExperience} is
 * its one consumer.
 *
 * <p>Its veto is {@link #suppress()}. Valid only during dispatch.
 */
public interface FeralExperience extends ModuleEvent<Mob> {

    /** The resolver's test: the mob carries the Feral record. */
    static boolean passes(Mob mob) {
        return FeralData.has(mob);
    }

    /** The Feral mob that died. */
    default Mob mob() {
        return context();
    }

    /** Drops no experience: the native event is cancelled, so no orb is created. */
    void suppress();
}
