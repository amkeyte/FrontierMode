package com.arryn.frontiermode.admin.client;

import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.platform.PlatformBound;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Registers the debug view's client command on {@code neoforge-1.21.1}: the loader's client and server command graphs
 * are separate, so this runs locally regardless of the server's own {@code /admin} tree. One call into
 * {@link AdminView}, which holds the command and everything it does. Client-only ({@code Dist.CLIENT}).
 */
@PlatformBound(reason = "commands")
@EventBusSubscriber(modid = FrontierIds.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class AdminViewClient {

    private AdminViewClient() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AdminView.registerCommands(event.getDispatcher());
    }
}
