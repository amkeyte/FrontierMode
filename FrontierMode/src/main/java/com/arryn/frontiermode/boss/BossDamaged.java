package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/**
 * A boss is about to take damage, after armor, protection and absorption: the module's event for Forge's
 * {@link LivingDamageEvent}, server only, at the lowest Forge priority so the amount is what other listeners left. Its
 * context is the boss's mob scope. It is built only for a boss, so the ingress's resolver is the cheap filter.
 * {@code CreditTally} reads it to tally the damage players deal (boss.md § Player credit and non-player death).
 */
public final class BossDamaged extends ModuleSignal<LivingDamageEvent, ScopeInfo> {

    BossDamaged(LivingDamageEvent forgeEvent, ScopeInfo bossScope) {
        super(forgeEvent, bossScope);
    }

    /** What the damage came from. */
    public DamageSource source() {
        return nativeEvent().getSource();
    }

    /**
     * The health this hit actually removes: the amount about to come off, capped at the boss's current health. Forge
     * does not cap it, so overkill is left uncounted here (MobData § Combat events).
     */
    public float healthRemoved() {
        LivingEntity entity = nativeEvent().getEntity();
        return Math.max(0f, Math.min(nativeEvent().getAmount(), entity.getHealth()));
    }
}
