package com.arryn.frontiermode.border.client.render.level;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class WorldBordersRenderer {

    private static final int SEGMENTS = 128;

    /** Total radial thickness = 0.3m */
    private static final float RING_HALF_THICKNESS = 0.15f;

    // Whether the rings draw is the debug view's call (admin-tools.md, Debug view): a deliberate player choice,
    // not a data-readiness check, which is RenderContext.standby()'s. Border never imports Admin, so the admin
    // client registers this supplier once at startup (AdminViewStartup). Until then, and by default, nothing draws.
    private static BooleanSupplier visibleWhen = () -> false;

    public static void setVisibleWhen(BooleanSupplier supplier) {
        visibleWhen = Objects.requireNonNull(supplier, "supplier");
    }



    // ---------------------------------------------------------------------
    // Public entry point
    // ---------------------------------------------------------------------

    public void render(PoseStack poseStack) {
        if (!visibleWhen.getAsBoolean()) return;

        // The client-only reads are here, not in RenderContext, which a dedicated server constructs too.
        // The level is null after a disconnect, while Minecraft's fields are already cleared.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RenderContext ctx = RenderContext.getInstance(mc.level)
                .filter(rc -> !rc.standby())
                .orElse(null);

        if (ctx == null) return;

        var camera = mc.gameRenderer.getMainCamera();
        var camPos = camera.getPosition();
        var borders = ctx.borders();
        var buffers = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        try {
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            for (Border border : borders) {
                renderOne(border, poseStack, buffers, camera);
            }

            // CRITICAL: flush buffered geometry so later passes (including particles) render correctly
            buffers.endBatch();
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
        Vector3f color = RingColorPalette.get(border.layer());

        // Was a hardcoded RING_Y = 100.0f, completely ignoring the border's own center height --
        // rendered fine (no crash, no error), just wherever Y 100 happened to land relative to
        // real terrain, which could easily be underground or otherwise depth-occluded depending
        // on the world. It needed the border's own center().getY() instead of a magic constant.
        // See FRO_019.
        double cx = border.center().getX();
        double cy = border.center().getY();
        double cz = border.center().getZ();

        drawRingBand(
                matrix,
                vc,
                (float) cx,
                (float) cy,
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
            float centerY,
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
            vertex(vc, matrix, ix1, centerY, iz1, color);
            vertex(vc, matrix, ox1, centerY, oz1, color);
            vertex(vc, matrix, ox2, centerY, oz2, color);

            vertex(vc, matrix, ix1, centerY, iz1, color);
            vertex(vc, matrix, ox2, centerY, oz2, color);
            vertex(vc, matrix, ix2, centerY, iz2, color);
        }
    }

    private static void vertex(
            VertexConsumer vc,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            Vector3f color
    ) {
        vc.vertex(matrix, x, y, z)
                .color(color.x(), color.y(), color.z(), 1.0f)
                .endVertex();
    }
}
