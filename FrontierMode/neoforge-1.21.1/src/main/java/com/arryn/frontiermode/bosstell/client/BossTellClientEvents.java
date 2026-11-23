package com.arryn.frontiermode.bosstell.client;

import com.arryn.frontiermode.bosstell.BossTellRegisters;
import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Client wiring for the Amethyst Eye's flight (amethyst-eye.md § The flight is vanilla's): vanilla's
 * {@link ThrownItemRenderer} draws the thrown Eye as the item it carries. MOD-bus, {@code Dist.CLIENT}-only, so no
 * client class loads on a dedicated server.
 */
@PlatformBound(reason = "render")
@EventBusSubscriber(modid = FrontierIds.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BossTellClientEvents {

    private BossTellClientEvents() {
    }

    /** Scale 1.0 and full bright, the arguments vanilla registers for its own Eye of Ender. */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BossTellRegisters.AMETHYST_EYE_ENTITY.get(),
                context -> new ThrownItemRenderer<>(context, 1.0F, true));
    }
}
