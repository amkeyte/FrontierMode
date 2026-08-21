package com.arryn.frontiermode.border.client.render.level;

import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.server.rules.DefaultBorderRules;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Client-side visual affordance for the border growth trigger.
 * <p>
 * Emits subtle particles at the grounded center of the path tip.
 * Purely visual: no authority, no persistence, no gameplay logic.
 */
public final class GrowthTriggerRenderer {

    private static final double VIEW_DISTANCE = 32.0;
    private static final RandomSource RNG = RandomSource.create();

    private static int radius = DefaultBorderRules.GROWTH_RING_RADIUS;

    private GrowthTriggerRenderer() {
    }

    public static void setRadius(int r) {
        radius = r;
    }

    /**
     * RM_FRO_012: entry point for {@code Rendering.onClientUnload} (subscribed to
     * {@code ScopeEvent.Unloaded} via {@code BorderModule}'s {@code EventHandlers}) to evict this
     * level's {@link RenderContext} -- {@code Rendering} lives in a different package and
     * {@code RenderContext} is package-private by design (client-render-only state, not meant to
     * be touched outside this package), so this is the public seam, same as {@link #tick()} is
     * for the per-tick call.
     */
    public static void onUnload(LevelScope scope) {
        RenderContext.evict(scope);
    }

    /**
     * Called once per client tick (END phase).
     */
    public static void tick() {
        // RM_FRO_012: was `debugFlame()`, called unconditionally here first thing, every client
        // tick, with no flag or config gate -- spawned a FLAME particle above the player's head
        // visible to every player, always. Rendering.onClientTick already had an almost identical
        // block commented out a few lines below its own call site, a strong signal this was meant
        // to be disabled the same way and got missed when the call site moved. Deleted outright
        // rather than gated behind a debug flag -- nothing about its prior form looked
        // intentional (see RM_FRO_012's roadmap node).

        RenderContext rc = RenderContext.getInstance()
                .filter(rcx -> !rcx.standby())
                .orElse(null);

        if (rc == null) return;

        LevelScope scope = rc.scope;
        //safe cast because we are on client stack
        ClientLevel level = (ClientLevel)scope.level();



        Optional<Border> tipOpt = rc.pathTip();
        if (tipOpt.isEmpty()) {
            return;
        }

        Border tip = rc.pathTip().orElse(null);
        if (tip == null) return;

        // Resolve grounded anchor at the tip center
        BlockPos center = tip.center();
        int groundY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                center.getX(),
                center.getZ()
        );

        BlockPos anchor = new BlockPos(
                center.getX(),
                groundY,
                center.getZ()
        );

        // Distance culling

        double camDistSq = rc.camera().getPosition()
                .distanceToSqr(
                        anchor.getX() + 0.5,
                        anchor.getY() + 0.5,
                        anchor.getZ() + 0.5
                );

        if (camDistSq > VIEW_DISTANCE * VIEW_DISTANCE) {
            return;
        }

        Vector3f color = RingColorPalette.get(tip.layer());
        spawnParticles(level, anchor, color);
    }

    /* --------------------------------------------------------------------- */
    /* Particle emission                                                      */
    /* --------------------------------------------------------------------- */

    private static void spawnParticles(ClientLevel level, BlockPos anchor, Vector3f color) {

        // Light continuous sparkle within ritual radius
        for (int i = 0; i < 4; i++) {

            double angle = RNG.nextDouble() * Math.PI * 2.0;
            double r = RNG.nextDouble() * radius;

            double fx = anchor.getX() + 0.5 + Math.cos(angle) * r;
            double fz = anchor.getZ() + 0.5 + Math.sin(angle) * r;
            double fy = anchor.getY() + 0.05;

            BlockPos candidate = BlockPos.containing(fx, fy, fz);

            // 🔑 Single source of truth for containment
            if (!BorderMath.isInside(radius, candidate, anchor)) {
                continue;
            }

            level.addParticle(
                    ParticleTypes.END_ROD,
                    fx, fy, fz,
                    0.0,
                    0.02,
                    0.0
            );
        }

        // Subtle center emphasis (low frequency)
        if (RNG.nextInt(10) == 0) {
            level.addParticle(
                    ParticleTypes.END_ROD,
                    anchor.getX() + 0.5,
                    anchor.getY() + 0.15,
                    anchor.getZ() + 0.5,
                    0.0,
                    0.03,
                    0.0
            );
        }
    }

}
