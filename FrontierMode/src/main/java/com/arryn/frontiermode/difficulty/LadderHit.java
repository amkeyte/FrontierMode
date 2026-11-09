package com.arryn.frontiermode.difficulty;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * A hit is about to land, and whatever dealt it carries a ladder damage multiplier: the module's event for
 * Forge's {@link LivingHurtEvent} (difficulty.md § Damage scales when it lands). Its context is the
 * attacker's {@code frontiermode:ladder} record, as {@code MobData.readAttacker} read it. The ingress's
 * resolver passes only a hit whose record holds a {@code damage} multiplier, so every other hit in the game
 * is dropped before an event is built. {@link DifficultyHandlers#onLadderHit} is its one consumer.
 *
 * <p>It fires before armor, so armor still reduces a scaled hit. {@link #amount()} reads the live event, so
 * it reflects any earlier listener's {@link #setAmount}. Valid only during dispatch.
 */
public final class LadderHit extends ModuleSignal<LivingHurtEvent, CompoundTag> {

    LadderHit(LivingHurtEvent forgeEvent, CompoundTag ladder) {
        super(forgeEvent, ladder);
    }

    /** The attacker's ladder record: its rung and its health and damage multipliers. */
    public CompoundTag ladder() {
        return context();
    }

    /** The damage about to be applied, before armor. Live. */
    public float amount() {
        return nativeEvent().getAmount();
    }

    public void setAmount(float amount) {
        nativeEvent().setAmount(amount);
    }
}
