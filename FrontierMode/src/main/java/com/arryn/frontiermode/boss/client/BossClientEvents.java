package com.arryn.frontiermode.boss.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.boss.common.BossMobTypes;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * FRO_145: client-side wiring for the boss eye layer -- adds a {@link BossEyesLayer} to each of the
 * boss-eligible mob renderer ({@link BossMobTypes#ORDERED}, Rabbit through Ravager). The set is
 * bounded, not open-ended: each model's UV layout needs its own texture, so adding a mob type to
 * the list means adding a texture named for its registry path.
 *
 * <p>MOD-bus, {@code Dist.CLIENT}-only (same registration shape as {@code AmethystCompassClient}),
 * so none of these client classes are ever loaded on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BossClientEvents {

    private BossClientEvents() {
    }

    /**
     * Adds the eye layer to every boss-eligible type's renderer. The eye texture is named for the
     * type's registry path, and a type with no texture fails loudly here, at startup, instead of
     * silently rendering without eyes.
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<? extends Mob> type : BossMobTypes.ORDERED) {
            addEyes(event, type, EntityType.getKey(type).getPath());
        }
    }

    private static <T extends LivingEntity> void addEyes(
            EntityRenderersEvent.AddLayers event, EntityType<T> type, String textureName) {
        LivingEntityRenderer<T, ? extends EntityModel<T>> renderer = event.getRenderer(type);
        if (renderer == null) {
            OUT.warn("[Boss] No renderer for " + type + " -- boss eyes not added for it.");
            return;
        }
        ResourceLocation texture = new ResourceLocation(FrontierMode.MODID, "textures/entity/boss_eyes/" + textureName + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) {
            throw new IllegalStateException("Boss-eligible type " + type + " has no eye texture at " + texture);
        }
        attach(renderer, texture);
    }

    // Separate generic method so the renderer's wildcard model type is captured as M.
    private static <T extends LivingEntity, M extends EntityModel<T>> void attach(
            LivingEntityRenderer<T, M> renderer, ResourceLocation texture) {
        renderer.addLayer(new BossEyesLayer<>(renderer, texture));
    }
}
