package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.platform.PlatformBound;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * The module's direct NeoForge touches that cannot ride the ingress because they fire before any level
 * loads. NeoForge posts {@link VillagerTradesEvent} once per profession from its own
 * {@code ServerAboutToStartEvent} listener, before the server's first level exists, and the ingress
 * drops a firing until the server foundation is bound and ready. The touch is permanent, not a
 * placeholder (wiki special-compass.md, "Forge touch points").
 *
 * <p>Each handler is one line delegating into loader-free logic.
 */
@PlatformBound(reason = "pre-level-listeners")
@EventBusSubscriber(modid = FrontierIds.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class PreLevelListeners {

    private PreLevelListeners() {
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        ResonanceTrade.addTo(event.getType(), event.getTrades());
    }
}
