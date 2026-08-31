package com.arryn.frontiermode.border.common;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Pure geometry helpers for border containment and distance.
 *
 * All calculations are performed in X/Z space only.
 * Y is intentionally ignored so that math matches rendered borders.
 *
 * This class favors clarity and correctness over micro-optimizations.
 */
public final class BorderMath {

    private BorderMath() {}

    /**
     * Returns true if {@code pos} lies inside or exactly on the border.
     * Uses true Euclidean distance in X/Z space.
     */
    public static boolean isInside(Border border, BlockPos pos) {
        if (border == null || pos == null) {
            return false;
        }

        return isInside(border.radius(), pos, border.center());
    }

    /**
     * Returns true if {@code pos} lies within {@code range} (inclusive),
     * measured in X/Z space from {@code center}.
     *
     * Y is intentionally ignored.
     */
    public static boolean isInside(int range, BlockPos pos, BlockPos center) {
        if (pos == null || center == null) {
            return false;
        }

        double dx = pos.getX() - center.getX();
        double dz = pos.getZ() - center.getZ();

        return (dx * dx + dz * dz) <= (range * range);
    }


    /**
     * Returns the distance from {@code pos} to the border surface.
     *
     * - 0 → inside or on the border
     * - N → N blocks outside the border
     *
     * Distance is computed in X/Z space only.
     */
    public static int distanceToSurface(Border border, BlockPos pos) {
        if (border == null || pos == null) {
            return 0;
        }

        return (int) (distanceSqToCenter(border,pos)-border.radius());
    }

    public static double distanceSqToCenter(Border border, BlockPos pos) {
        double dx = pos.getX() - border.center().getX();
        double dz = pos.getZ() - border.center().getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Uniform-in-disk XZ sample, centered on {@code center}, radius {@code radius} -- the same
     * angle/distance shape {@code DefaultBorderRules.chooseNextCenter()} already uses for its own
     * (much smaller, fixed-30-block) offset, generalized to an arbitrary radius and a real
     * uniform-area distribution rather than a fixed-length hop.
     *
     * <p>Y is intentionally left as {@code center}'s own Y (a placeholder -- see {@link Border}'s
     * XZ-only geometry contract). Callers needing a real ground position (RM_FRO_018's Boss
     * materialization step) resolve Y separately, only once the target chunk is confirmed loaded
     * -- picking a position and resolving its ground height are two different steps for exactly
     * the reason documented on {@code boss.md}'s "Spawn algorithm" section: chunk-loaded state has
     * nothing to do with *where* the column is, only with *when* a block-data-dependent Y can be
     * read.
     *
     * <p>Uses {@code sqrt(rng.nextDouble())} for the radial component so points are uniform across
     * the disk's *area*, not uniform in radius (which would bias samples toward the center) --
     * standard uniform-disk-sampling shape, not something either existing caller needed before
     * since {@code chooseNextCenter()}'s own offset is a fixed length, not an area sample.
     */
    public static BlockPos randomPointInDisk(RandomSource rng, BlockPos center, int radius) {
        if (center == null) {
            throw new IllegalArgumentException("center must not be null");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative: " + radius);
        }

        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(rng.nextDouble()) * radius;

        int dx = (int) Math.round(Math.cos(angle) * dist);
        int dz = (int) Math.round(Math.sin(angle) * dist);

        return new BlockPos(
                center.getX() + dx,
                center.getY(),
                center.getZ() + dz
        );
    }

    // ------------------------------------------------------------------
    // RM_FRO_026 ("Dorothy"): Navigator's two point-to-point primitives -- see
    // wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border. Deliberately
    // no player, target-type, or attunement awareness, and deliberately no nearest() companion:
    // "nearest of N candidates" is a one-line min() over a caller-specific pre-filtered candidate
    // list, per that page's own reasoning -- folding it in here would only paper over call sites
    // that still need their own "what's relevant" logic regardless.
    //
    // Both methods are thin BlockPos-unwrapping wrappers over BorderMathLogic -- the actual math
    // lives there so it can be unit-tested without a Minecraft userdev classpath (see that
    // class's own doc).
    // ------------------------------------------------------------------

    /**
     * Straight-line XZ distance between {@code a} and {@code b}. Y is ignored, per this class's
     * own contract.
     */
    public static double distanceTo(BlockPos a, BlockPos b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");

        return BorderMathLogic.distanceTo(a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    /**
     * Normalized XZ direction from {@code from} to {@code to}, as a {@link Vec3} with
     * {@code y=0}. {@link Vec3#ZERO} when the two points coincide -- there is no direction
     * between a point and itself.
     */
    public static Vec3 direction(BlockPos from, BlockPos to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        double[] d = BorderMathLogic.direction(from.getX(), from.getZ(), to.getX(), to.getZ());
        return new Vec3(d[0], 0.0, d[1]);
    }

    // ------------------------------------------------------------------
    // RM_FRO_027 ("Janet"): BorderCurve evaluation -- see
    // wiki/frontiermode/architecture/border-curve.md#evaluation-bordermath-not-a-service-class.
    // BorderCurve is data; the evaluation is pure math living here, same as isInside/
    // randomPointInDisk above. Zero domain knowledge either way -- this doesn't know what a
    // "tier" or "guardian" is, only how to turn a shape and a distance into a number.
    // ------------------------------------------------------------------

    /**
     * The shape function core: {@code normalizedDistance} (0 at the border's center, 1 at its
     * edge) mapped through {@code descriptor}'s shape into an intensity in {@code [0, 1]}.
     */
    public static double intensityAt(BorderCurve descriptor, double normalizedDistance) {
        Objects.requireNonNull(descriptor, "descriptor");

        return BorderCurveMath.intensityAt(descriptor.shape(), descriptor.steepness(), normalizedDistance);
    }

    /**
     * Convenience wrapper doing the resolve-distance-then-normalize-then-evaluate steps in one
     * call, per that page's own "Evaluation" section -- {@code normalizedDistance =
     * distanceTo(center, point) / radius}.
     */
    public static double intensityAt(Border border, BorderCurve descriptor, BlockPos point) {
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(point, "point");

        double normalizedDistance = distanceTo(border.center(), point) / border.radius();
        return intensityAt(descriptor, normalizedDistance);
    }

}
