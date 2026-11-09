package com.arryn.frontiermode.admin.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registers, once at client startup, the supplier the border ring renderer asks whether to draw: the debug view's
 * flag for the local player. Border never imports Admin, so the registration is Admin's
 * (wiki admin-tools.md, "Debug view"; border.md, "Commands and client surface"). Client-only ({@code Dist.CLIENT}).
 */
@PlatformBound(reason = "registration")
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AdminViewStartup {

    private AdminViewStartup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> WorldBordersRenderer.setVisibleWhen(AdminViewClient::viewOn));
    }
}
