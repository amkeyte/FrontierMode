package com.arryn.frontiermode.bosstell.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.bosstell.BossTellModule;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client wiring for the Amethyst Eye's flight (amethyst-eye.md § The flight is vanilla's): vanilla's
 * {@link ThrownItemRenderer} draws the thrown Eye as the item it carries. MOD-bus, {@code Dist.CLIENT}-only, so no
 * client class loads on a dedicated server.
 */
@PlatformBound(reason = "render")
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BossTellClientEvents {

    private BossTellClientEvents() {
    }

    /** Scale 1.0 and full bright, the arguments vanilla registers for its own Eye of Ender. */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BossTellModule.AMETHYST_EYE_ENTITY.get(),
                context -> new ThrownItemRenderer<>(context, 1.0F, true));
    }
}
