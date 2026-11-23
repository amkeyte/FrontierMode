package com.arryn.frontiermode.core;

import net.minecraft.world.entity.Mob;

/**
 * Removes a mob with vanilla's death puff and nothing else (wiki/frontiermode/architecture/core.md): no drops, no
 * experience, no death event. Boss uses it for a body whose record is defeated and for the copy a dimension change
 * leaves behind; the Sentinel module uses it for a Sentinel whose boss is gone. It takes a helper here because more than
 * one module calls it. Server thread only, called from its callers' Trinket ticks and handlers.
 */
public final class Dismissal {

    private Dismissal() {
    }

    /**
     * Discards {@code mob} with vanilla's death puff (entity event 60). {@code discard()}, not {@code kill()}, so the
     * real-death handlers do not run.
     */
    public static void dismiss(Mob mob) {
        mob.level().broadcastEntityEvent(mob, (byte) 60);
        mob.discard();
    }
}
