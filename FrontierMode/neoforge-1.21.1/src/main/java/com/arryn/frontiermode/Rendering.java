package com.arryn.frontiermode;

import com.arryn.frontiermode.admin.client.AdminViewMarkers;
import com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer;

import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.platform.PlatformBound;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

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
@EventBusSubscriber(modid = FrontierIds.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class Rendering {

    private Rendering() {
    }

    // Built on first use, not in a static initializer: WorldBordersRenderer names client-only types
    // (MultiBufferSource), and a dedicated server must never load it. onRenderLevel runs only on the client
    // (RenderLevelStageEvent does not fire on a dedicated server, and this class's own
    // @EventBusSubscriber(value = Dist.CLIENT) keeps Forge from registering it there), so this lazy field
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

        // The border rings
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            // The chunk-layer stages carry no pose stack on 1.21.1 (the camera is in the model-view matrix
            // already), so a fresh one stands in.
            PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
            bordersRenderer().render(pose);
        }
        // The debug view's markers, drawn where vanilla draws entity text (name tags); nothing while the view is off.
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
            AdminViewMarkers.render(pose, event.getCamera(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }
}
