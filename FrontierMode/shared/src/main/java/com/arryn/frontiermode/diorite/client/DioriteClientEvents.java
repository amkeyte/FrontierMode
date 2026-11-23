package com.arryn.frontiermode.diorite.client;

import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.frontiermode.diorite.DioriteRegisters;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client wiring for the Edge Stone: vanilla's {@link ThrownItemRenderer}, the snowball's, draws the
 * thrown item. MOD-bus, {@code Dist.CLIENT}-only, so no client class loads on a dedicated server.
 */
@PlatformBound(reason = "render")
@Mod.EventBusSubscriber(modid = FrontierIds.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DioriteClientEvents {

    private DioriteClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DioriteRegisters.EDGE_STONE_ENTITY.get(), ThrownItemRenderer::new);
    }
}
