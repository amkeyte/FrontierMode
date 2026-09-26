package com.arryn.frontiermode.boss.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.boss.EntityScales;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * FRO_148: the client half of boss (and guardian) scale -- the render transform, and the dimension refresh that
 * makes the client's own hitbox catch up once the marker's scale arrives.
 *
 * <p>The hitbox scale itself is {@code BossScaleEvents}' {@code Size} handler (both sides). This
 * adds what only the client needs: the marker syncs on Satchel's cadence, after the entity already
 * exists, so nothing else would call {@code refreshDimensions()} on the client when it lands.
 *
 * <p>Known limitation: the render scale wraps the whole {@code render()} call, which includes the
 * name tag, so a scaled boss's name tag sits and sizes slightly high. Vanilla exposes no hook to
 * scale only the model.
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, value = Dist.CLIENT)
public final class BossScaleClientEvents {

    // Scale last applied to each client entity's dimensions; weak so removed entities drop out.
    private static final Map<LivingEntity, Float> APPLIED = new WeakHashMap<>();

    private BossScaleClientEvents() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide || !EntityScales.canBeScaled(entity)) {
            return;
        }
        float scale = EntityScales.scaleOf(entity);
        if (scale != APPLIED.getOrDefault(entity, 1.0F)) {
            APPLIED.put(entity, scale);
            entity.refreshDimensions();
        }
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
