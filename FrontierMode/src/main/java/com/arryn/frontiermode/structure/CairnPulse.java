package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.structure.common.CairnFixture;
import com.arryn.frontiermode.structure.common.CairnRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the Cairn's pulse looks and sounds like, and where it points. See cairn.md's "The pulse" and
 * the Cairn design page.
 *
 * <p>One stream of dust leaves the cap straight up, then bends in a single smooth arc -- a
 * quadratic curve, like a fountain -- into the bearing, with no kink at the top. The launch height
 * and the length after the bend are rolled fresh every trigger. <b>Secrecy:</b> only the bearing
 * direction and those two independent rolls shape the stream; the raw target coordinate, and how
 * far away it is, never reach the client, and the curve's shape never depends on that distance.
 */
public final class CairnPulse {

    private static final int DEAD_SPHERE_RADIUS = 100;

    private static final RandomSource RNG = RandomSource.create();

    private CairnPulse() {
    }

    /**
     * The pulse -- right-click and redstone both call this identical handler
     * ({@code CairnCapBlock.use}/{@code neighborChanged}). Resolves where the pulse points, then fires it.
     */
    public static void triggerPulse(Level level, BlockPos capPos) {
        if (level.isClientSide) {
            return;
        }

        Optional<CairnFixture> fixtureOpt = StructureAPI.cairnFixture(level);
        if (fixtureOpt.isEmpty()) {
            return;
        }
        CairnFixture fixture = fixtureOpt.get();

        BlockPos anchorPos = capPos.below(CairnPlacer.PILLAR_HEIGHT);
        Optional<CairnRecord> recordOpt = fixture.getByAnchor(anchorPos);
        if (recordOpt.isEmpty()) {
            return;
        }
        CairnRecord cairn = recordOpt.get();
        UUID bossId = cairn.bossId();

        BlockPos target;
        if (BossAPI.isBossAlive(level, bossId)) {
            // "Just points to what it knows" -- overwrite this Cairn's cache on a successful live read,
            // leave it untouched (and still use it) on failure. No fallback chain beyond this.
            Optional<BlockPos> liveOpt = BossAPI.resolveLivePosition(level, bossId);
            if (liveOpt.isPresent()) {
                fixture.updateLastKnownBossPos(cairn.cairnId(), liveOpt.get());
                target = liveOpt.get();
            } else {
                target = cairn.lastKnownBossPos();
            }
        } else {
            // Dead path touches no boss state at all -- re-rolled every trigger, never cached.
            target = randomPointInSphere(CairnPlacer.RNG, anchorPos, DEAD_SPHERE_RADIUS);
        }

        fire(level, capPos, target);
    }

    /** Uniform random point within a sphere of {@code radius} centered on {@code center}. */
    @SuppressWarnings("SameParameterValue")
    static BlockPos randomPointInSphere(RandomSource rng, BlockPos center, int radius) {
        double u = rng.nextDouble();
        double r = radius * Math.cbrt(u);
        double theta = rng.nextDouble() * 2.0 * Math.PI;
        double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);

        double x = r * Math.sin(phi) * Math.cos(theta);
        double y = r * Math.sin(phi) * Math.sin(theta);
        double z = r * Math.cos(phi);

        return center.offset((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
    }

    /** Fires the pulse from {@code capPos} toward {@code target}'s bearing: the ping, then the stream. */
    static void fire(Level level, BlockPos capPos, BlockPos target) {
        Vec3 start = new Vec3(capPos.getX() + 0.5, capPos.getY() + 1.0, capPos.getZ() + 0.5);

        EffectsAPI.broadcastSound(level, start.x, start.y, start.z, FrontierMode.CAIRN_PING.get(),
                SoundSource.BLOCKS, CairnRules.pingVolume(), CairnRules.pingPitch());

        double launchHeight = roll(CairnRules.launchHeightMin(), CairnRules.launchHeightMax());
        double streamLength = roll(CairnRules.streamLengthMin(), CairnRules.streamLengthMax());

        // Bearing from the top of the launch, angled fully in three dimensions.
        Vec3 apex = start.add(0.0, launchHeight, 0.0);
        Vec3 direction = new Vec3(
                target.getX() + 0.5 - apex.x,
                target.getY() - apex.y,
                target.getZ() + 0.5 - apex.z
        ).normalize();

        for (Vec3 point : arc(start, launchHeight, direction, streamLength)) {
            EffectsAPI.broadcastParticle(level, EffectsAPI.AMETHYST_DUST, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Points along a quadratic curve that leaves {@code start} heading straight up and arrives
     * heading along {@code direction}: control points {@code start}, {@code start + up x launchHeight},
     * and that plus {@code direction x streamLength}. About one point per block of the control path.
     */
    static List<Vec3> arc(Vec3 start, double launchHeight, Vec3 direction, double streamLength) {
        Vec3 control = start.add(0.0, launchHeight, 0.0);
        Vec3 end = control.add(direction.scale(streamLength));

        int steps = Math.max(1, (int) Math.ceil(launchHeight + streamLength));
        List<Vec3> points = new ArrayList<>(steps);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double u = 1.0 - t;
            points.add(start.scale(u * u).add(control.scale(2.0 * u * t)).add(end.scale(t * t)));
        }
        return points;
    }

    private static double roll(double min, double max) {
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        return low + RNG.nextDouble() * (high - low);
    }
}
