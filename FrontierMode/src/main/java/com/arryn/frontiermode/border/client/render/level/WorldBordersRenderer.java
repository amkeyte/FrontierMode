package com.arryn.frontiermode.border.client.render.level;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class WorldBordersRenderer {

    private static final int SEGMENTS = 128;

    /** World height of the ring */
    private static final float RING_Y = 100.0f;

    /** Total radial thickness = 0.3m */
    private static final float RING_HALF_THICKNESS = 0.15f;



    // ---------------------------------------------------------------------
    // Public entry point
    // ---------------------------------------------------------------------

    public void render(PoseStack poseStack) {

        RenderContext ctx = RenderContext.getInstance()
                .filter(rc -> !rc.standby())
                .orElse(null);

        if (ctx == null) return;

        var camera = ctx.camera();
        var camPos = camera.getPosition();
        var borders = ctx.borders();
        var buffers = ctx.buffers();

        poseStack.pushPose();
        try {
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            for (Border border : borders) {
                renderOne(border, poseStack, buffers, camera);
            }

            // CRITICAL: flush buffered geometry so later passes (including particles) render correctly
            // RenderContext.buffers() appears to be a BufferSource in your apidump.
            // If your ctx.buffers() returns MultiBufferSource.BufferSource, this exists:
            //if (buffers instanceof MultiBufferSource.BufferSource bs) {
            buffers.endBatch();
            //}
        } finally {
            // CRITICAL: restore pose stack for subsequent render stages
            poseStack.popPose();
        }
    }
    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    private void renderOne(
            Border border,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera
    ) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        VertexConsumer vc =
                buffers.getBuffer(RenderType.debugFilledBox());

        float radius = border.radius();
        Vector3f color = RingColorPalette.get(border.layerIndex());

        double cx = border.center().getX();
        double cz = border.center().getZ();

        drawRingBand(
                matrix,
                vc,
                (float) cx,
                (float) cz,
                radius - RING_HALF_THICKNESS,
                radius + RING_HALF_THICKNESS,
                color
        );
    }

    // ---------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------

    private static void drawRingBand(
            Matrix4f matrix,
            VertexConsumer vc,
            float centerX,
            float centerZ,
            float innerRadius,
            float outerRadius,
            Vector3f color
    ) {
        float step = (float) (Math.PI * 2.0 / SEGMENTS);

        for (int i = 0; i < SEGMENTS; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            // Inner
            float ix1 = centerX + cos1 * innerRadius;
            float iz1 = centerZ + sin1 * innerRadius;
            float ix2 = centerX + cos2 * innerRadius;
            float iz2 = centerZ + sin2 * innerRadius;

            // Outer
            float ox1 = centerX + cos1 * outerRadius;
            float oz1 = centerZ + sin1 * outerRadius;
            float ox2 = centerX + cos2 * outerRadius;
            float oz2 = centerZ + sin2 * outerRadius;

            // Two triangles
            vertex(vc, matrix, ix1, iz1, color);
            vertex(vc, matrix, ox1, oz1, color);
            vertex(vc, matrix, ox2, oz2, color);

            vertex(vc, matrix, ix1, iz1, color);
            vertex(vc, matrix, ox2, oz2, color);
            vertex(vc, matrix, ix2, iz2, color);
        }
    }

    private static void vertex(
            VertexConsumer vc,
            Matrix4f matrix,
            float x,
            float z,
            Vector3f color
    ) {
        vc.vertex(matrix, x, RING_Y, z)
                .color(color.x(), color.y(), color.z(), 1.0f)
                .endVertex();
    }
}
