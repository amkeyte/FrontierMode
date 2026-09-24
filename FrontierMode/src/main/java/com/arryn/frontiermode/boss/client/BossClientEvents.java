package com.arryn.frontiermode.boss.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * FRO_145: client-side wiring for the boss eye layer -- adds a {@link BossEyesLayer} to each of the
 * eight boss-eligible mob renderers (boss.md's layer table, Rabbit through Ravager). The set is
 * bounded, not open-ended: each model's UV layout needs its own texture, so adding a mob type to
 * the boss table means adding a texture here.
 *
 * <p>MOD-bus, {@code Dist.CLIENT}-only (same registration shape as {@code AmethystCompassClient}),
 * so none of these client classes are ever loaded on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BossClientEvents {

    private BossClientEvents() {
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        addEyes(event, EntityType.RABBIT, "rabbit");
        addEyes(event, EntityType.SPIDER, "spider");
        addEyes(event, EntityType.ZOMBIE, "zombie");
        addEyes(event, EntityType.SKELETON, "skeleton");
        addEyes(event, EntityType.ZOMBIFIED_PIGLIN, "zombified_piglin");
        addEyes(event, EntityType.PILLAGER, "pillager");
        addEyes(event, EntityType.VINDICATOR, "vindicator");
        addEyes(event, EntityType.RAVAGER, "ravager");
    }

    private static <T extends LivingEntity> void addEyes(
            EntityRenderersEvent.AddLayers event, EntityType<T> type, String textureName) {
        LivingEntityRenderer<T, ? extends EntityModel<T>> renderer = event.getRenderer(type);
        if (renderer == null) {
            OUT.warn("[Boss] No renderer for " + type + " -- boss eyes not added for it.");
            return;
        }
        attach(renderer, new ResourceLocation(FrontierMode.MODID, "textures/entity/boss_eyes/" + textureName + ".png"));
    }

    // Separate generic method so the renderer's wildcard model type is captured as M.
    private static <T extends LivingEntity, M extends EntityModel<T>> void attach(
            LivingEntityRenderer<T, M> renderer, ResourceLocation texture) {
        renderer.addLayer(new BossEyesLayer<>(renderer, texture));
    }
}
