package com.arryn.frontiermode.keeper;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.LivingEntity;

/**
 * A mob is about to take a boss as its attack target: the module's event for the loader's change-target event, server
 * only, on both lines. Its context is the boss's mob scope, found for the new target. It is built only when the new
 * target is a boss, so each line's resolver is the cheap filter. {@code AllyTruce} cancels it when the changing mob is
 * an ally (keeper.md § Allies never attack a Keeper).
 */
public interface KeeperTargetChange extends ModuleEvent<ScopeInfo> {

    /** The mob that is changing its target. */
    LivingEntity changingMob();

    /** Keeps the mob's current target. */
    void cancel();
}
