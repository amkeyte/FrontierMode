package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * A hit is about to be applied by an attacker whose main-hand stack carries Death Touch: the module's event for the
 * loader's damage event at the stage before armor (Two Lines § Damage stages on each line), on both lines. Its context
 * is the attacker. That event fires for every hit on every living thing, so each line's resolver is the cheap filter and
 * an event is built only for a hit whose attacker's main hand {@link #passes}.
 */
public interface DeathTouchHit extends ModuleEvent<LivingEntity> {

    /** The resolver's test: the main-hand stack carries Death Touch. */
    static boolean passes(ItemStack mainHand) {
        return StackData.enchantmentLevel(mainHand, AdminToolsModule.DEATH_TOUCH.get()) > 0;
    }

    /** The attacker holding the Death Touch weapon. */
    default LivingEntity attacker() {
        return context();
    }

    /**
     * Raises the damage amount to {@code Float.MAX_VALUE} rather than killing the target from here, so the kill still
     * runs through the normal, already-in-progress damage pipeline (armor, absorption, death handling).
     */
    void overwhelm();
}
