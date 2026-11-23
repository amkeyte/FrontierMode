package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.damagesource.DamageSource;

/**
 * A boss is about to take damage, and the amount can still be changed: the module's event for the loader's damage
 * event at the stage after armor and magic reduction (Two Lines § Damage stages on each line), server only. It is the
 * earlier of the module's two stages: {@link BossDamaged} follows and reads what this stage left. Its context is the
 * boss's mob scope. It is built only for a boss, so each line's resolver is the cheap filter. {@code ArmorAnswer} hears
 * it to let a hit land at one over the armor multiplier (boss.md § The ArmorAnswer ability).
 *
 * <p>{@link #amount()} reads the live event, so it reflects an earlier listener's {@link #setAmount}. Valid only during
 * dispatch.
 */
public interface BossTakingDamage extends ModuleEvent<ScopeInfo> {

    /** What the damage came from. */
    DamageSource source();

    /** The damage about to come off, after armor. Live. */
    float amount();

    void setAmount(float amount);
}
