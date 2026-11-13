package com.arryn.frontiermode.difficulty;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * A hit is about to land, and whatever dealt it carries a ladder damage multiplier: the module's event for
 * Forge's {@link LivingHurtEvent} (difficulty.md § Damage scales when it lands). Its context is that
 * multiplier, which {@code Ladder} read off the attacker's {@code frontiermode:ladder} record. The ingress's
 * resolver passes only a hit whose attacker holds one, so every other hit in the game is dropped before an
 * event is built. {@link DifficultyHandlers#onLadderHit} is its one consumer.
 *
 * <p>It fires before armor, so armor still reduces a scaled hit. {@link #amount()} reads the live event, so
 * it reflects any earlier listener's {@link #setAmount}. Valid only during dispatch.
 */
public final class LadderHit extends ModuleSignal<LivingHurtEvent, Double> {

    LadderHit(LivingHurtEvent forgeEvent, Double multiplier) {
        super(forgeEvent, multiplier);
    }

    /** The attacker's damage multiplier. */
    public double multiplier() {
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
