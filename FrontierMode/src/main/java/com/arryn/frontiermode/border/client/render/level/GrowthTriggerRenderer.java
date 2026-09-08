package com.arryn.frontiermode.border.client.render.level;

import com.arryn.frontiermode.border.BorderAPI;
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
 * Emits subtle particles at a grounded, deterministic approximation of the paired boss's
 * position -- not the border's own geometric center, and deliberately not the boss's real
 * position either. Purely visual: no authority, no persistence, no gameplay logic.
 * <p>
 * FRO_094 (building FRO_093's Architect ruling): boss location is deliberately secret from the
 * client (see {@code boss.md} § Module wiring), so this can never resolve a real {@code
 * BossRecord} -- there isn't one on the client to read. Instead it reconstructs a stable,
 * edge-biased point from data the client already legitimately has (the synced {@link Border}'s
 * own id/center/radius), close enough to "where a real boss would plausibly be" for a decorative
 * debug/admin aura without ever holding the true answer. See {@link #approximateBossPosition}.
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

        // Resolve grounded anchor at the approximate (never real) boss position -- FRO_094
        BlockPos approxBoss = approximateBossPosition(tip);
        int groundY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                approxBoss.getX(),
                approxBoss.getZ()
        );

        BlockPos anchor = new BlockPos(
                approxBoss.getX(),
                groundY,
                approxBoss.getZ()
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
    /* Boss-position approximation (FRO_094)                                  */
    /* --------------------------------------------------------------------- */

    // Mirrors DefaultBossRules.EDGE_BIAS_INNER_FRACTION's *shape* (sample the outer share of the
    // radius, biased toward the edge, since new borders crawl outward from wherever their boss
    // died) without depending on that class -- FRO_093's ruling was explicit that this build adds
    // no Boss-package touch. This is a visual approximation matching the general placement
    // pattern, not a reference to boss-package internals or a claim of reproducing its exact
    // output.
    private static final double APPROX_EDGE_BIAS_INNER_FRACTION = 0.6;

    /**
     * Deterministic, per-border, edge-biased point standing in for the paired boss's real
     * position. Never crosses into boss-owned data or state -- seeded purely from the {@link
     * Border} the client already has synced, so the same border always resolves to the same
     * point (a stable anchor, not a different guess every tick) and no two distinct borders
     * collide on the same seed.
     */
    private static BlockPos approximateBossPosition(Border border) {
        RandomSource seeded = RandomSource.create(
                border.id().getMostSignificantBits() ^ border.id().getLeastSignificantBits());

        int innerRadius = (int) Math.round(border.radius() * APPROX_EDGE_BIAS_INNER_FRACTION);

        return BorderAPI.MATH.randomPointInAnnulus(seeded, border.center(), innerRadius, border.radius());
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
            if (!BorderAPI.MATH.isInside(radius, candidate, anchor)) {
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
