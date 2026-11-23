package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.damagesource.DamageSource;

/**
 * A boss has taken damage, and the amount is what every other listener left: the module's event for the loader's
 * damage event at its last stage (Two Lines § Damage stages on each line), server only. It is the later of the module's
 * two stages: {@link BossTakingDamage} comes first and may change the amount. Its context is the boss's mob scope. It is
 * built only for a boss, so each line's resolver is the cheap filter. {@code CreditTally} reads it to tally the damage
 * players deal (boss.md § Player credit and non-player death).
 */
public interface BossDamaged extends ModuleEvent<ScopeInfo> {

    /** What the damage came from. */
    DamageSource source();

    /**
     * The health this hit actually removes: the amount that came off, capped at the boss's health before the hit, so
     * overkill is left uncounted here (MobData § Combat events).
     */
    float healthRemoved();
}
