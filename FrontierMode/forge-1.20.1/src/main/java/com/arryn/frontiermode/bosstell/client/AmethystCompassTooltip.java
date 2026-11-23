package com.arryn.frontiermode.bosstell.client;

import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Amethyst Compass's tooltip line on {@code forge-1.20.1} (Two Lines § Overrides whose form differs): the item
 * tooltip event, which both loaders post, adds the line {@link AmethystCompassItem#tooltipLine} gives for the stack.
 * It replaces an override of the item's tooltip method, whose form differs by line. Client-only ({@code Dist.CLIENT}).
 */
@PlatformBound(reason = "render")
@Mod.EventBusSubscriber(modid = FrontierIds.MOD_ID, value = Dist.CLIENT)
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
