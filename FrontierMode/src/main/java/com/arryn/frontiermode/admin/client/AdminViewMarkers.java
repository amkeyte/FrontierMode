package com.arryn.frontiermode.admin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * The debug view's marker: a coloured diamond drawn above every mob {@link AdminViewClient} is
 * marking, seen through walls, so a playtester can find a tracked mob and tell what it's there for
 * (FRO_202). Gold for a boss, purple for a sentinel, green for a Feral.
 *
 * <p>A font glyph drawn the way vanilla draws a name tag's see-through pass, but fully opaque
 * (vanilla's see-through pass is ~12% alpha, which is what makes distant tags faint). It has a fixed
 * size in the world, so it shrinks with distance -- its size reads as how far away the mob is.
 * Called from {@code Rendering}'s existing {@code RenderLevelStageEvent} subscriber.
 */
public final class AdminViewMarkers {

    private static final String GLYPH = "◆";

    // Ten times vanilla's name-tag scale (0.025): the glyph is about two blocks tall.
    private static final float MARKER_SCALE = 0.25F;

    // Above the name tag when the mob shows one; at the tag's height when it doesn't.
    private static final double ABOVE_TAG = 0.45;

    private AdminViewMarkers() {
    }

    public static void render(PoseStack pose, Camera camera, float partialTick) {
        Map<Mob, AdminViewClient.Role> marked = AdminViewClient.marked();
        if (marked.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float halfWidth = font.width(GLYPH) / 2.0F;

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
            // Text draws downward from its origin; start one line up so the glyph sits above the
            // anchor instead of hanging over the mob.
            font.drawInBatch(GLYPH, -halfWidth, -font.lineHeight, role.color(), false, matrix, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        });
        buffers.endBatch();
    }
}
