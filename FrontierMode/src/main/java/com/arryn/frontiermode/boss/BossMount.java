package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraftforge.event.entity.EntityMountEvent;

/**
 * A boss is about to mount something, or something is about to mount it, or either is about to dismount: the module's
 * event for Forge's {@link EntityMountEvent}, server only. Its context is the boss's mob scope, found for the entity
 * mounting or the entity being mounted. It is built only when one of them is a boss, so the ingress's resolver is the
 * cheap filter. {@code NoRiding} cancels a mount and never a dismount (boss.md § NoRiding).
 */
public final class BossMount extends ModuleSignal<EntityMountEvent, ScopeInfo> {

    BossMount(EntityMountEvent forgeEvent, ScopeInfo bossScope) {
        super(forgeEvent, bossScope);
    }

    /** True for a mount, false for a dismount. */
    public boolean isMounting() {
        return nativeEvent().isMounting();
    }

    /** Stops the mount. */
    public void cancel() {
        nativeEvent().setCanceled(true);
    }
}
