package com.arryn.frontiermode.keeper.client;

import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.frontiermode.keeper.server.KeeperKind;
import com.arryn.frontiermode.keeper.common.EyeBrightness;
import com.arryn.satchel.common.platform.PlatformBound;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * draws shining amethyst eyes on a boss -- the same technique vanilla uses for
 * Enderman/Spider/Warden ({@code RenderType.eyes}, full-bright, so it reads at night the same as
 * by day). One layer instance per boss-eligible mob type, each with that model's own eye-overlay
 * texture (UV layouts differ per species); see {@link BossClientEvents}.
 *
 * <p>The per-entity gate is "does the client's own roster hold this entity's UUID with an alive record of the
 * Keeper kind" (via {@link BossMarkers}) -- not the vanilla {@code Glowing} tag or a
 * scoreboard team (boss.md's "Client-side visual marker"). Plain vanilla mobs of the same type, and another
 * kind's bosses, are untouched.
 *
 * <p>The eyes show how hurt the Keeper is (keeper.md § Client-side eyes and flash): the brightness scales with the
 * body's health over its maximum, both of which the client already holds, down to a floor so a hurt Keeper stays
 * findable. The brightness is {@link EyeBrightness}'s; how it becomes the draw call's colour is this line's.
 */
@PlatformBound(reason = "render")
public final class BossEyesLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private final RenderType renderType;

    public BossEyesLayer(RenderLayerParent<T, M> parent, ResourceLocation texture) {
        super(parent);
        this.renderType = RenderType.eyes(texture);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (!BossMarkers.kindOf(entity).filter(KeeperKind.KEY::equals).isPresent()) {
            return;
        }
        VertexConsumer vc = buffer.getBuffer(renderType);
        float brightness = EyeBrightness.of(entity.getHealth(), entity.getMaxHealth());
        getParentModel().renderToBuffer(pose, vc, 15728640, OverlayTexture.NO_OVERLAY,
                brightness, brightness, brightness, 1.0F);
    }
}
