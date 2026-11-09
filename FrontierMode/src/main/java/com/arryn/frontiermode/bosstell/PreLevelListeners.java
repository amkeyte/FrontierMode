package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The module's direct Forge touches that cannot ride the ingress because they fire before any level
 * loads. Forge posts {@link VillagerTradesEvent} once per profession from its own
 * {@code ServerAboutToStartEvent} listener, before the server's first level exists, and the ingress
 * drops a firing until the server foundation is bound and ready. The touch is permanent, not a
 * placeholder (wiki special-compass.md, "Forge touch points").
 *
 * <p>Each handler is one line delegating into Forge-free logic.
 */
@PlatformBound(reason = "pre-level-listeners")
@Mod.EventBusSubscriber(modid = FrontierMode.MODID)
public final class PreLevelListeners {

    private PreLevelListeners() {
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        ResonanceTrade.addTo(event.getType(), event.getTrades());
    }
}
