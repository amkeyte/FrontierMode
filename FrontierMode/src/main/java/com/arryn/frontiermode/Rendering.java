package com.arryn.frontiermode;

import com.arryn.frontiermode.border.client.render.level.GrowthTriggerRenderer;
import com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.lifecycle.SatchelEvent;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side rendering and visual tick hooks.
 *
 * This class is responsible only for visual affordances:
 *  - border rendering
 *  - particle effects
 *
 * No authority, no level mutation.
 */
@Mod.EventBusSubscriber(
        modid = FrontierMode.MODID,
        value = Dist.CLIENT
)
public class Rendering {

    private static final WorldBordersRenderer BORDERS_RENDERER =
            new WorldBordersRenderer();

    /* --------------------------------------------------------------------- */
    /* Render pass                                                            */
    /* --------------------------------------------------------------------- */

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (Satchel.foundation()
                .filter(f -> f.side().isClient())
                .isEmpty()) return;

        // Render border rings
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            BORDERS_RENDERER.render(event.getPoseStack());
        }
//        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
//            // Spawn visual-only particles (growth trigger, etc.)
//            GrowthTriggerRenderer.tick();
//        }
    }


    public static void onClientTick(ScopeEvent.Tick event) {

        if (Satchel.foundation()
                .filter(f -> f.side().isClient())
                .isEmpty()) return;



        // Spawn visual-only particles (growth trigger, etc.)
        GrowthTriggerRenderer.tick();

//        Minecraft mc = Minecraft.getInstance();
//        if (mc.level == null || mc.player == null) return;
//        if (mc.isPaused()) return;
//
//        mc.level.addParticle(
//                ParticleTypes.FLAME,
//                mc.player.getX(),
//                mc.player.getY() + 2,
//                mc.player.getZ(),
//                0.0, 0.05, 0.0
//        );
    }
}
