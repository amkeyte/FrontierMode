package com.arryn.frontiermode.bosstell.client;

import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.platform.PlatformBound;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The Amethyst Compass's tooltip line on {@code neoforge-1.21.1} (Two Lines § Overrides whose form differs): the item
 * tooltip event, which both loaders post, adds the line {@link AmethystCompassItem#tooltipLine} gives for the stack.
 * It replaces an override of the item's tooltip method, whose form differs by line. Client-only ({@code Dist.CLIENT}).
 */
@PlatformBound(reason = "render")
@EventBusSubscriber(modid = FrontierIds.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class AmethystCompassTooltip {

    private AmethystCompassTooltip() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof AmethystCompassItem) {
            AmethystCompassItem.tooltipLine(event.getItemStack()).ifPresent(event.getToolTip()::add);
        }
    }
}
