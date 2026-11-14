package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/**
 * A boss is about to take damage, and the amount can still be changed: the module's event for Forge's
 * {@link LivingDamageEvent}, server only, at Forge's normal priority. The amount is after armor, protection and
 * absorption and is open to {@link #setAmount}. It is the earlier of the module's two stages on that event:
 * {@link BossDamaged} follows at the lowest priority and reads what this stage left. Its context is the boss's mob
 * scope. It is built only for a boss, so the ingress's resolver is the cheap filter. {@code ArmorAnswer} hears it to
 * let a hit land at one over the armor multiplier (boss.md § The ArmorAnswer ability).
 *
 * <p>{@link #amount()} reads the live event, so it reflects an earlier listener's {@link #setAmount}. Valid only during
 * dispatch.
 */
public final class BossTakingDamage extends ModuleSignal<LivingDamageEvent, ScopeInfo> {

    BossTakingDamage(LivingDamageEvent forgeEvent, ScopeInfo bossScope) {
        super(forgeEvent, bossScope);
    }

    /** What the damage came from. */
    public DamageSource source() {
        return nativeEvent().getSource();
    }

    /** The damage about to come off, after armor. Live. */
    public float amount() {
        return nativeEvent().getAmount();
    }

    public void setAmount(float amount) {
        nativeEvent().setAmount(amount);
    }
}
