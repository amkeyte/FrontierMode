package com.arryn.frontiermode.boss.client;

import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.frontiermode.boss.common.fixture.BossPublicFixture;
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
 * FRO_145: draws shining amethyst eyes on a boss -- the same technique vanilla uses for
 * Enderman/Spider/Warden ({@code RenderType.eyes}, full-bright, so it reads at night the same as
 * by day). One layer instance per boss-eligible mob type, each with that model's own eye-overlay
 * texture (UV layouts differ per species); see {@link BossClientEvents}.
 *
 * <p>The per-entity gate is "does this entity's UUID appear, alive, in the client's own copy of
 * {@link BossPublicFixture}" (via {@link BossMarkers}) -- not the vanilla {@code Glowing} tag or a
 * scoreboard team (boss.md's "Client-side visual marker"). Plain vanilla mobs of the same type are
 * untouched.
 */
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
        if (!BossMarkers.isLiveBoss(entity)) {
            return;
        }
        VertexConsumer vc = buffer.getBuffer(renderType);
        getParentModel().renderToBuffer(pose, vc, 15728640, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
