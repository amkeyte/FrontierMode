package com.arryn.frontiermode.admin.client;

import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the debug view's client command on {@code forge-1.20.1}: Forge's client and server command graphs are
 * separate, so this runs locally regardless of the server's own {@code /admin} tree. One call into {@link AdminView},
 * which holds the command and everything it does. Client-only ({@code Dist.CLIENT}).
 */
@PlatformBound(reason = "commands")
@Mod.EventBusSubscriber(modid = FrontierIds.MOD_ID, value = Dist.CLIENT)
public final class AdminViewClient {

    private AdminViewClient() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AdminView.registerCommands(event.getDispatcher());
    }
}
