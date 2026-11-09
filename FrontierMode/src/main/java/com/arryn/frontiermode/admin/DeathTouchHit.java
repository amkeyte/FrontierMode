package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * A hit is about to be applied by an attacker whose main-hand stack carries Death Touch: the module's
 * event for Forge's {@link LivingHurtEvent}. Its context is the attacker. {@code LivingHurtEvent} fires for
 * every hit on every living thing, so the ingress's resolver is the cheap filter and an event is built only
 * for a Death Touch hit.
 */
public final class DeathTouchHit extends ModuleSignal<LivingHurtEvent, LivingEntity> {

    DeathTouchHit(LivingHurtEvent forgeEvent, LivingEntity attacker) {
        super(forgeEvent, attacker);
    }

    /** The attacker holding the Death Touch weapon. */
    public LivingEntity attacker() {
        return context();
    }

    /**
     * Raises the damage amount to {@code Float.MAX_VALUE} rather than killing the target from here, so the
     * kill still runs through the normal, already-in-progress damage pipeline (armor, absorption, death
     * handling).
     */
    public void overwhelm() {
        nativeEvent().setAmount(Float.MAX_VALUE);
    }
}
