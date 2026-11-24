package com.arryn.frontiermode.admin.client;

import com.arryn.frontiermode.admin.common.Role;
import com.arryn.satchel.common.platform.PlatformBound;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * The debug view's marker: a coloured diamond drawn above every mob {@link AdminView} is
 * marking, seen through walls, so a playtester can find a tracked mob and tell what it's there for.
 * Gold for a boss, purple for a sentinel, green for a Feral.
 *
 * <p>A flat, half-opaque quad that faces the camera, drawn with {@code RenderType.guiOverlay()}, which tests no
 * depth, so a wall does not hide it. On 1.21.1 a font glyph never drew in the level, though flat geometry did, so both lines draw the same diamond. It has a fixed size in the world, so it shrinks with distance -- its size
 * reads as how far away the mob is. Called from {@code Rendering}'s {@code RenderLevelStageEvent} subscriber.
 */
@PlatformBound(reason = "render")
public final class AdminViewMarkers {

    // Seven and a half times vanilla's name-tag scale (0.025): the diamond is about one and a half blocks tall.
    private static final float MARKER_SCALE = 0.1875F;

    // The diamond's half-width and half-height in those units, and the height of its centre above the anchor.
    private static final float HALF = 5.0F;
    private static final float CENTRE = 6.0F;

    // Above the name tag when the mob shows one; at the tag's height when it doesn't.
    private static final double ABOVE_TAG = 0.45;

    // Alpha of the diamond, 0 to 255.
    private static final int ALPHA = 0x80;

    private AdminViewMarkers() {
    }

    public static void render(PoseStack pose, Camera camera, float partialTick) {
        Map<Mob, Role> marked = AdminView.marked();
        if (marked.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer quad = buffers.getBuffer(RenderType.guiOverlay());
        Vec3 cam = camera.getPosition();

        marked.forEach((mob, role) -> {
            if (mob.isRemoved()) {
                return;
            }
            double x = Mth.lerp(partialTick, mob.xOld, mob.getX());
            double y = Mth.lerp(partialTick, mob.yOld, mob.getY())
                    + mob.getNameTagOffsetY() + (role.tagged() ? ABOVE_TAG : 0.0);
            double z = Mth.lerp(partialTick, mob.zOld, mob.getZ());
            pose.pushPose();
            pose.translate(x - cam.x, y - cam.y, z - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(-MARKER_SCALE, -MARKER_SCALE, MARKER_SCALE);
            Matrix4f matrix = pose.last().pose();
            // Half opaque: the role's colour with its alpha replaced.
            int rgb = role.color();
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;
            int alpha = ALPHA;
            float top = -CENTRE - HALF;
            float bottom = -CENTRE + HALF;
            // Both windings, so back-face culling can never hide it.
            quad.vertex(matrix, 0, top, 0).color(red, green, blue, alpha).endVertex();
            quad.vertex(matrix, -HALF, -CENTRE, 0).color(red, green, blue, alpha).endVertex();
            quad.vertex(matrix, 0, bottom, 0).color(red, green, blue, alpha).endVertex();
            quad.vertex(matrix, HALF, -CENTRE, 0).color(red, green, blue, alpha).endVertex();
            quad.vertex(matrix, 0, top, 0).color(red, green, blue, alpha).endVertex();
            quad.vertex(matrix, HALF, -CENTRE, 0).color(red, green, blue, alpha).endVertex();
            quad.vertex(matrix, 0, bottom, 0).color(red, green, blue, alpha).endVertex();
            quad.vertex(matrix, -HALF, -CENTRE, 0).color(red, green, blue, alpha).endVertex();
            pose.popPose();
        });
        buffers.endBatch(RenderType.guiOverlay());
    }
}
