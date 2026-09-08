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
 *
 * <p><b>Public for structural reasons only, same as {@code BordersFixture}
 * (see that class's own "External Access Is Not Compiler-Enforced" doc) -- {@link
 * com.arryn.frontiermode.border.BorderAPI}, the sanctioned facade for everything outside
 * {@code border.common}, lives in a different package ({@code com.arryn.frontiermode.border})
 * and cannot reach a package-private member here, so literal package-privacy was never available.
 * {@code BorderAPI.MATH} (FRO_078) is the sanctioned path for anything outside this package --
 * enforced by doc-comment discipline, not the compiler, same mitigation {@code BordersFixture}
 * already accepts for the identical structural reason.</b>
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
     * Returns the signed distance from {@code pos} to the border surface.
     *
     * - negative → inside the border (how far inside)
     * - 0 → exactly on the border
     * - positive → outside the border (how far outside)
     *
     * Distance is computed in X/Z space only.
     *
     * <p>FRO_099 bugfix: this used to compute {@code distanceSqToCenter(border, pos) -
     * border.radius()} -- subtracting a linear radius from a *squared* center distance, which is
     * dimensionally wrong (it degenerates to something close to correct only very near the
     * border's edge, and is badly off everywhere else). Fixed to a real center distance via
     * {@link BorderMathLogic#distanceToEdge}, the same pure core {@link #distanceOutside} below
     * now shares.
     */
    public static int distanceToSurface(Border border, BlockPos pos) {
        if (border == null || pos == null) {
            return 0;
        }

        double centerDistance = BorderMathLogic.distanceTo(
                pos.getX(), pos.getZ(), border.center().getX(), border.center().getZ());

        return (int) Math.round(BorderMathLogic.distanceToEdge(centerDistance, border.radius()));
    }

    /**
     * RM_FRO_037 ("Brenda," Frontier Sickness epoch 1): disc-edge distance -- {@code 0} when
     * {@code p} is inside or on the disc described by {@code center}/{@code radius}, the XZ gap
     * otherwise. Unlike {@link #distanceToSurface}, this is never negative -- it answers "how far
     * *outside* this one border," not "how far from its edge in either direction," which is
     * exactly what a Frontier-distance reduction (a min over every established Border, per
     * wiki/frontiermode/architecture/exterior.md#the-distance-to-frontier-query) needs: a point
     * inside any single Border contributes 0 to that min, not a large negative number that would
     * wrongly win it.
     *
     * <p>Y is intentionally ignored, matching every other Border geometry query -- a Border is a
     * full-height cylinder.
     */
    public static int distanceOutside(BlockPos p, BlockPos center, int radius) {
        if (p == null || center == null) {
            return 0;
        }

        double centerDistance = BorderMathLogic.distanceTo(p.getX(), p.getZ(), center.getX(), center.getZ());
        return (int) Math.round(Math.max(0.0, BorderMathLogic.distanceToEdge(centerDistance, radius)));
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

    /**
     * Like {@link #randomPointInDisk}, but excludes everything inside {@code innerRadius} --
     * area-uniform across the annulus {@code [innerRadius, outerRadius]} rather than the whole
     * disk. {@code innerRadius == 0} reduces to exactly {@link #randomPointInDisk}'s own
     * distribution (same formula, degenerate inner bound).
     *
     * <p>Added for Boss placement (FRO_072): {@code center}/{@code outerRadius} here are meant
     * to be one and the same border's own center and radius -- both bounds share one circle, so
     * (unlike trying to exclude a *different*, differently-centered border's disk) this is a
     * real, exact closed-form region. See {@code DefaultBossRules.choosePosition}'s own
     * {@code EDGE_BIAS_INNER_FRACTION} for why: each new border centers on wherever its boss
     * died, so a boss placed near its own border's center barely moves the next border's center
     * either -- biasing placement toward this same border's outer edge is what makes the whole
     * chain of borders actually walk outward (the "crawl" this project's border-path design
     * intends) instead of nesting almost concentrically on top of each other.
     */
    public static BlockPos randomPointInAnnulus(RandomSource rng, BlockPos center, int innerRadius, int outerRadius) {
        if (center == null) {
            throw new IllegalArgumentException("center must not be null");
        }
        if (innerRadius < 0) {
            throw new IllegalArgumentException("innerRadius must not be negative: " + innerRadius);
        }
        if (outerRadius < innerRadius) {
            throw new IllegalArgumentException(
                    "outerRadius must not be less than innerRadius: " + outerRadius + " < " + innerRadius);
        }

        double angle = rng.nextDouble() * Math.PI * 2.0;
        double innerSq = (double) innerRadius * innerRadius;
        double outerSq = (double) outerRadius * outerRadius;
        double dist = Math.sqrt(innerSq + rng.nextDouble() * (outerSq - innerSq));

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
