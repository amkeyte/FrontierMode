package com.arryn.frontiermode.diorite;

import com.arryn.frontiermode.FrontierMode;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registering an item does not put it in any creative tab. Dust and shard go under Ingredients, the
 * Striker under Tools and Utilities beside flint and steel (diorite-tools.md § Module shape).
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DioriteCreativeTabs {

    private DioriteCreativeTabs() {
    }

    @SubscribeEvent
    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(DioriteItems.DIORITE_DUST);
            event.accept(DioriteItems.DIORITE_SHARD);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DioriteItems.DIORITE_STRIKER);
        }
    }
}
