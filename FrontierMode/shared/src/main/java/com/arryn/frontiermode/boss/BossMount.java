package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * A boss is about to mount something, or something is about to mount it, or either is about to dismount: the module's
 * event for the loader's entity-mount event, server only, on both lines. Its context is the boss's mob scope, found for
 * the entity mounting or the entity being mounted. It is built only when one of them is a boss, so each line's resolver
 * is the cheap filter. {@code NoRiding} cancels a mount and never a dismount (boss.md § NoRiding).
 */
public interface BossMount extends ModuleEvent<ScopeInfo> {

    /** True for a mount, false for a dismount. */
    boolean isMounting();

    /** Stops the mount. */
    void cancel();
}
