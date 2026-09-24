package com.arryn.frontiermode.boss.client;

import com.arryn.frontiermode.boss.BossAPI;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * FRO_145: draws shining amethyst eyes on a boss -- the same technique vanilla uses for
 * Enderman/Spider/Warden ({@code RenderType.eyes}, full-bright, so it reads at night the same as
 * by day). One layer instance per boss-eligible mob type, each with that model's own eye-overlay
 * texture (UV layouts differ per species); see {@link BossClientEvents}.
 *
 * <p>The per-entity gate is "does this entity's UUID appear, alive, in the client's own copy of
 * {@link BossPublicFixture}" -- not the vanilla {@code Glowing} tag or a scoreboard team (boss.md's
 * "Client-side visual marker"). Plain vanilla mobs of the same type are untouched.
 */
public final class BossEyesLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    // Resolving the fixture goes through Satchel scope lookup -- too much for every zombie on every
    // frame -- so it's cached per level, and retried at most once a second while unresolved (the
    // normal state for the first moments after joining, before the first parcel lands).
    private static final long RETRY_TICKS = 20;
    private static Level cachedLevel;
    private static BossPublicFixture cachedFixture;
    private static long nextResolveTick;

    private final RenderType renderType;

    public BossEyesLayer(RenderLayerParent<T, M> parent, ResourceLocation texture) {
        super(parent);
        this.renderType = RenderType.eyes(texture);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (!isBoss(entity)) {
            return;
        }
        VertexConsumer vc = buffer.getBuffer(renderType);
        getParentModel().renderToBuffer(pose, vc, 15728640, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static boolean isBoss(Entity entity) {
        Level level = entity.level();
        if (level != cachedLevel) {
            cachedLevel = level;
            cachedFixture = null;
            nextResolveTick = 0;
        }
        if (cachedFixture == null) {
            long now = level.getGameTime();
            if (now < nextResolveTick) {
                return false;
            }
            cachedFixture = BossAPI.publicMarkers(level).orElse(null);
            if (cachedFixture == null) {
                nextResolveTick = now + RETRY_TICKS;
                return false;
            }
        }
        return cachedFixture.isLiveBoss(entity.getUUID());
    }
}
