package com.arryn.frontiermode.keeper;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraftforge.event.entity.living.LivingConversionEvent;

/**
 * A boss is about to convert into another type (a Zombie Keeper drowning into a Drowned): the module's event for
 * Forge's {@link LivingConversionEvent.Pre}, server only. Its context is the boss's mob scope, found for the
 * converting mob. It is built only when that mob is a boss, so the ingress's resolver is the cheap filter.
 * {@code NoConvert} cancels it (keeper.md § A live Keeper never converts).
 */
public final class KeeperConversion extends ModuleSignal<LivingConversionEvent.Pre, ScopeInfo> {

    KeeperConversion(LivingConversionEvent.Pre forgeEvent, ScopeInfo bossScope) {
        super(forgeEvent, bossScope);
    }

    /** Keeps the mob as it is. */
    public void cancel() {
        nativeEvent().setCanceled(true);
    }
}
