package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Cairn's pulse looks and sounds like, once {@code StructureModule} has resolved where it
 * points. See cairn.md's "The pulse" and the Cairn design page.
 *
 * <p>One stream of dust leaves the cap straight up, then bends in a single smooth arc -- a
 * quadratic curve, like a fountain -- into the bearing, with no kink at the top. The launch height
 * and the length after the bend are rolled fresh every trigger. <b>Secrecy:</b> only the bearing
 * direction and those two independent rolls shape the stream; the raw target coordinate, and how
 * far away it is, never reach the client, and the curve's shape never depends on that distance.
 */
final class CairnPulse {

    private static final RandomSource RNG = RandomSource.create();

    private CairnPulse() {
    }

    /** Fires the pulse from {@code capPos} toward {@code target}'s bearing: the ping, then the stream. */
    static void fire(ServerLevel level, BlockPos capPos, BlockPos target) {
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
