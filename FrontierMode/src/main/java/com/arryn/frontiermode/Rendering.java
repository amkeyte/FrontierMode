package com.arryn.frontiermode;

import com.arryn.frontiermode.border.client.render.level.GrowthTriggerRenderer;
import com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.level.LevelScope;
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

    // Crash fix (first real dedicated-server run, run-server/logs, 2026-08-16): was
    // `private static final WorldBordersRenderer BORDERS_RENDERER = new WorldBordersRenderer();`
    // -- an eager static field initializer, which runs during Rendering.<clinit>. BorderModule's
    // EventHandlers wires Rendering::onClientTick (and onClientUnload) into a BOTH-applicability
    // LevelJigConfig, so ScopeEvent.Tick invokes those methods on the server too -- and merely
    // *entering* either method already forces <clinit> to run first, before either method's own
    // client-side guard ever gets a chance to return early. <clinit> unconditionally constructing
    // a WorldBordersRenderer forced that class to load, and WorldBordersRenderer references
    // MultiBufferSource in its method signatures -- a class Forge's RuntimeDistCleaner refuses to
    // load on DEDICATED_SERVER. Crashed the very first server tick.
    //
    // This was invisible under every prior singleplayer/integrated test (client classes are
    // legitimately loadable there) and only surfaced now that a real dedicated server is being
    // run for the first time -- same class of risk the sidedness-facade vision page already
    // named. Fix: defer construction until onRenderLevel actually needs it -- onRenderLevel is
    // only ever invoked client-side (RenderLevelStageEvent doesn't fire on a dedicated server, and
    // this class's own @Mod.EventBusSubscriber(value = Dist.CLIENT) keeps Forge from even
    // registering it there), so WorldBordersRenderer.class now never loads on the server at all.
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
        if (Satchel.foundation()
                .filter(f -> f.side().isClient())
                .isEmpty()) return;

        // Render border rings
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            bordersRenderer().render(event.getPoseStack());
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

    /**
     * RM_FRO_012: newly subscribed to {@code ScopeEvent.Unloaded} via {@code BorderModule}'s
     * {@code EventHandlers}, alongside the existing {@code Tick} subscription above --
     * {@code RenderContext.CACHE} was never evicted before this; see that class's own doc for the
     * full reasoning. Same client-side guard as {@link #onClientTick}, same shared-bus caveat
     * (this fires for every LevelJig scope unload on this dimension, not just Border's -- eviction
     * is idempotent, so that's harmless here).
     */
    public static void onClientUnload(ScopeEvent.Unloaded event) {

        if (Satchel.foundation()
                .filter(f -> f.side().isClient())
                .isEmpty()) return;

        // Defensive instanceof rather than a blind info().scopeAs() cast: this handler is
        // subscribed on the shared per-side SatchelEventBus, so it fires for ScopeEvent.Unloaded
        // from ANY jig, not just Border's LevelJig -- harmless to skip a non-LevelScope quietly
        // rather than risk a ClassCastException if a future client-applicable jig kind is ever
        // added.
        if (!(event.info().scope() instanceof LevelScope scope)) return;

        GrowthTriggerRenderer.onUnload(scope);
    }
}
