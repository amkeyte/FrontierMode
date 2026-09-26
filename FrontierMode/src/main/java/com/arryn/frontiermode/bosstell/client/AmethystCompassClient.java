package com.arryn.frontiermode.bosstell.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.bosstell.BossTellModule;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only wiring for {@code AmethystCompassItem}'s visual -- checked against the real
 * 1.20.1-47.4.10 sources, not assumed (see FRO_118's log): vanilla's needle-rotation model
 * system is registered per exact {@code Item} instance
 * ({@code ItemProperties.register(Items.COMPASS, "angle", ...)}, a {@code Map<Item, ...>} keyed
 * by reference), never for "anything {@code instanceof CompassItem}." Extending
 * {@code CompassItem} in Java gives {@code AmethystCompassItem} the NBT helpers
 * ({@code isLodestoneCompass}/{@code getLodestonePosition}) and vanilla's default interaction
 * behavior, but not the rendering -- that has to be registered separately, for this exact item,
 * which is what this class does.
 *
 * <p><b>The "angle" lambda differs from {@code ItemProperties}' own {@code Items.COMPASS}
 * registration in exactly one place, deliberately:</b> it returns {@code null} instead of
 * falling back to {@code CompassItem.getSpawnPosition(level)} when the stack isn't a lodestone
 * compass. Vanilla's own compass points at world spawn as that fallback (only landing on the
 * "spin randomly" branch, via {@code isValidCompassTargetPos} rejecting a null target, in a
 * non-natural dimension like the Nether). An un-attuned Amethyst Compass has no real target at
 * all -- project owner's call: it should spin randomly everywhere, the same way a vanilla
 * compass already does in the Nether, not point at spawn in the Overworld. Once actually
 * attuned, {@code AmethystCompassItem.refreshTarget}/{@code relock} write the same real
 * {@code LodestonePos}/{@code LodestoneDimension} NBT keys vanilla's own compass reads, so the
 * lodestone branch below is unchanged.
 *
 * <p><b>Visual: 32 edited texture frames, no runtime tint.</b> An earlier pass here tried a
 * two-layer split (vanilla's own casing+needle texture plus a needle-only cutout, tinted via a
 * registered {@code ItemColor}) to get a purple needle without new texture assets. Superseded --
 * the project owner recolored the needle directly, per-frame, in the actual texture files
 * ({@code textures/item/amethyst_compass_00.png}..{@code _31.png}, the needle's red half
 * repainted purple, casing and the needle's white half left untouched). Color is baked into the
 * pixels now, so {@code models/item/amethyst_compass_XX.json} is back to a single
 * {@code layer0} pointing at those files directly, and no {@code RegisterColorHandlersEvent}
 * registration is needed at all -- only the "angle" property below still applies.
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AmethystCompassClient {

    private AmethystCompassClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ItemProperties.register(
                BossTellModule.AMETHYST_COMPASS.get(),
                ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) ->
                        StackData.lodestonePosition(stack).orElse(null))
        );
    }

    /**
     * Not wired anywhere else -- registering an {@code Item} does not, by itself, put it in any
     * creative-inventory tab. Never craftable (no recipe exists at all), but creative-menu
     * pickup is a separate, legitimate acquisition path for an admin/debug tool -- same
     * category as vanilla's own command-block or spawn-egg items, which have no recipe either
     * but are still grabbable from creative. Placed in Tools and Utilities, matching where
     * vanilla's own {@code Compass} lives.
     */
    @SubscribeEvent
    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BossTellModule.AMETHYST_COMPASS);
        }
    }
}
