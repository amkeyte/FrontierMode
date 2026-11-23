package com.arryn.frontiermode.diorite;

import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Registering an item does not put it in any creative tab. Dust and shard go under Ingredients, the
 * Striker under Tools and Utilities beside flint and steel (diorite-tools.md § Module shape).
 */
@PlatformBound(reason = "registration")
@EventBusSubscriber(modid = FrontierIds.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DioriteCreativeTabs {

    private DioriteCreativeTabs() {
    }

    @SubscribeEvent
    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(DioriteRegisters.DIORITE_DUST.get());
            event.accept(DioriteRegisters.DIORITE_SHARD.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DioriteRegisters.DIORITE_STRIKER.get());
            event.accept(DioriteRegisters.EDGE_STONE.get());
        }
    }
}
