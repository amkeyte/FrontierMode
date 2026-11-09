package com.arryn.frontiermode.core.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.core.EntityScales;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * the render transform for a scaled entity (a boss or a Sentinel), client only -- the hitbox scale is
 * {@code CoreIngress}' consumer (both sides), and the dimension refresh that makes the client's own hitbox catch up is
 * the boss's {@code BodyScale} ability and the Sentinel's client Trinket. The scale comes from {@link EntityScales},
 * which the render handler reads for every drawn entity on every frame, which a request event per read cannot serve.
 *
 * <p>Known limitation: the render scale wraps the whole {@code render()} call, which includes the
 * name tag, so a scaled boss's name tag sits and sizes slightly high. Vanilla exposes no hook to
 * scale only the model.
 */
@PlatformBound(reason = "render")
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, value = Dist.CLIENT)
public final class ScaleRenderEvents {

    private ScaleRenderEvents() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        float scale = EntityScales.scaleOf(event.getEntity());
        if (scale != 1.0F) {
            event.getPoseStack().pushPose();
            event.getPoseStack().scale(scale, scale, scale);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (EntityScales.scaleOf(event.getEntity()) != 1.0F) {
            event.getPoseStack().popPose();
        }
    }
}
