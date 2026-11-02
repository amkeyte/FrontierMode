package com.arryn.frontiermode.keeper;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;

/**
 * A mob is about to take a boss as its attack target: the module's event for Forge's {@link LivingChangeTargetEvent},
 * server only. Its context is the boss's mob scope, found for the new target. It is built only when the new target is
 * a boss, so the ingress's resolver is the cheap filter. {@code AllyTruce} cancels it when the changing mob is an
 * ally (keeper.md § Allies never attack a Keeper).
 */
public final class KeeperTargetChange extends ModuleSignal<LivingChangeTargetEvent, ScopeInfo> {

    KeeperTargetChange(LivingChangeTargetEvent forgeEvent, ScopeInfo bossScope) {
        super(forgeEvent, bossScope);
    }

    /** The mob that is changing its target. */
    public LivingEntity changingMob() {
        return nativeEvent().getEntity();
    }

    /** Keeps the mob's current target. */
    public void cancel() {
        nativeEvent().setCanceled(true);
    }
}
