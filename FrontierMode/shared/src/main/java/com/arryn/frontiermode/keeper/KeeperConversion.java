package com.arryn.frontiermode.keeper;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * A boss is about to convert into another type (a Zombie Keeper drowning into a Drowned): the module's event for the
 * loader's pre-conversion event, server only, on both lines. Its context is the boss's mob scope, found for the
 * converting mob. It is built only when that mob is a boss, so each line's resolver is the cheap filter.
 * {@code NoConvert} cancels it (keeper.md § A live Keeper never converts).
 */
public interface KeeperConversion extends ModuleEvent<ScopeInfo> {

    /** Keeps the mob as it is. */
    void cancel();
}
