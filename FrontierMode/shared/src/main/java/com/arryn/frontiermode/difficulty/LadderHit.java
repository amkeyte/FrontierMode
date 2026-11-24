package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.difficulty.server.DifficultyHandlers;
import com.arryn.satchel.common.ingress.ModuleEvent;

/**
 * A hit is about to land, and whatever dealt it carries a ladder damage multiplier: the module's event for the loader's
 * incoming-damage event, on both lines (difficulty.md § Damage scales when it lands). Its context is that multiplier,
 * which {@code Ladder} read off the attacker's {@code frontiermode:ladder} record. Each line's resolver passes only a hit
 * whose attacker holds one, so every other hit in the game is dropped before an event is built.
 * {@link DifficultyHandlers#onLadderHit} is its one consumer.
 *
 * <p>It fires before armor, so armor still reduces a scaled hit (Two Lines § Damage stages on each line).
 * {@link #amount()} reads the live event, so it reflects any earlier listener's {@link #setAmount}. Valid only during
 * dispatch.
 */
public interface LadderHit extends ModuleEvent<Double> {

    /** The attacker's damage multiplier. */
    default double multiplier() {
        return context();
    }

    /** The damage about to be applied, before armor. Live. */
    float amount();

    void setAmount(float amount);
}
