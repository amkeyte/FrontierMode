package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierMode;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * FRO_148: a boss's (and, per {@link EntityScales}, a guardian's) real (non-cosmetic) hitbox scale, on plain Forge events -- no Pehkui, no
 * mixin (boss.md's "Spawn platform"/"Client-side visual marker"). Both sides register this: the
 * server needs the scaled hitbox for collision and combat, the client for its own prediction and
 * culling. The scale itself comes from {@link EntityScales}: the boss lookup, else the guardian lookup, each
 * reading a synced fixture, so server and client agree from one value.
 *
 * <p>{@code EntityEvent.Size} fires from the {@code Entity} constructor (before anything knows the
 * mob is a boss -- no marker yet, so a no-op) and from every {@code refreshDimensions()} after.
 * The attach hook and the client tick handler call {@code refreshDimensions()} once the scale is
 * known. Eye height rides on the same event ({@code setNewSize(size, true)}) -- the separate
 * {@code EntityEvent.EyeHeight} is deprecated and no longer fired by Forge or vanilla.
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID)
public final class BossScaleEvents {

    private BossScaleEvents() {
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        float scale = EntityScales.scaleOf(event.getEntity());
        if (scale != 1.0F) {
            EntityDimensions scaled = event.getNewSize().scale(scale);
            event.setNewSize(scaled, true);
        }
    }
}
