package com.arryn.frontiermode;

import com.arryn.frontiermode.admin.client.AdminViewMarkers;
import com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer;

import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side rendering and visual tick hooks.
 * <p>
 * This class is responsible only for visual affordances:
 *  - border rendering
 *  - particle effects
 * <p>
 * No authority, no level mutation.
 */
@PlatformBound(reason = "render")
@Mod.EventBusSubscriber(
        modid = FrontierMode.MODID,
        value = Dist.CLIENT
)
public class Rendering {

    // Built on first use, not in a static initializer: WorldBordersRenderer names client-only types
    // (MultiBufferSource), and a dedicated server must never load it. onRenderLevel runs only on the client
    // (RenderLevelStageEvent does not fire on a dedicated server, and this class's own
    // @Mod.EventBusSubscriber(value = Dist.CLIENT) keeps Forge from registering it there), so this lazy field
    // keeps WorldBordersRenderer off the server.
    private static WorldBordersRenderer bordersRenderer;

    private static WorldBordersRenderer bordersRenderer() {
        if (bordersRenderer == null) {
            bordersRenderer = new WorldBordersRenderer();
        }
        return bordersRenderer;
    }

    /* --------------------------------------------------------------------- */
    /* Render pass                                                            */
    /* --------------------------------------------------------------------- */

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // No side check: this method is reached only through Forge's Dist-gated @SubscribeEvent dispatch, and
        // RenderLevelStageEvent never fires on a dedicated server.

        // Render border rings
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            bordersRenderer().render(event.getPoseStack());
            // The debug view's markers; draws nothing while the view is off.
            AdminViewMarkers.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        }
    }
}
