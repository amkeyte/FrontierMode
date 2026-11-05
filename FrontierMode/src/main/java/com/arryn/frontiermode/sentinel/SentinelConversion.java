package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.Shape;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.frontiermode.keeper.KeeperItems;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.frontiermode.spawn.MobSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The conversion decision (sentinels.md § The two curves and the conversion decision, § Size and adds): which
 * spawn types can become a Sentinel, the nearest Keeper and its two curves, the two gates, the placement roll,
 * the size that fits, the stat step and the ward, and how many extra Sentinels the leftover budget buys. Stateless:
 * what is counted, queued or lifted is {@link SentinelPresence}'s. The bodies were {@code BossSentinelsFixture}'s
 * and moved here unchanged with node G (FRO_292).
 */
final class SentinelConversion {

    private static final String PURPOSE_PLACEMENT = "placement";
    private static final String PURPOSE_DIFFICULTY = "difficulty";

    // Placement is LINEAR over a fraction of the border radius (see sentinelPlacementRadiusFraction).
    private static final Shape PLACEMENT_SHAPE = Shape.LINEAR;
    private static final double PLACEMENT_STEEPNESS = 1.0; // ignored by LINEAR
    private static final Shape DIFFICULTY_SHAPE = Shape.LOG;
    private static final double DIFFICULTY_STEEPNESS = 1.0;

    // Shared RNG -- same static-field, not-persisted shape BossTellFixture.RNG uses.
    private static final RandomSource RNG = RandomSource.create();

    /** What one spawn position resolves to: its nearest boss and Keeper record, that boss's border, and the two curves. */
    record Nearby(BossRecord boss, KeeperRecord keeper, double radius, BorderCurve placement,
                  BorderCurve difficulty, double distanceToBoss) {
    }

    private SentinelConversion() {
    }

    /**
     * Whether a spawn of {@code type} can become a Sentinel, and so whether the daylight gates may lift for it.
     * {@code MobCategory.MONSTER} is the base test (not {@code instanceof Monster}: Slime and MagmaCube are
     * {@code MONSTER}-category hostiles that don't extend it). Excluded: Phantom, which ignites in sunlight with no
     * helmet check so the sun ward can't protect it; and Slime and MagmaCube, which split on death and would crowd the
     * hostile cap.
     */
    static boolean canBeSentinel(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER
                && type != EntityType.PHANTOM
                && type != EntityType.SLIME
                && type != EntityType.MAGMA_CUBE;
    }

    /**
     * Ensures {@code borderId} carries a {@code "placement"} and a {@code "difficulty"}
     * {@link BorderCurve} matching this class's own current {@code PLACEMENT_SHAPE}/
     * {@code DIFFICULTY_SHAPE} constants -- creates either if absent (mirroring
     * {@code BossTellFixture.createTellCurveIfAbsent}'s idempotent shape), but also
     * reconciles an existing record whose shape/steepness has drifted from the current constants
     * via {@link BorderCurveFixture#replace}, so a live world that seeded a curve under an older
     * "safe baseline, replace later" value picks up a later tuning pass without a world reset.
     * Called from {@link SentinelIngress}'s consumer of {@code KeeperCreated}, and once more from
     * {@link #resolveNearby} when a curve is missing.
     */
    static void ensureSentinelCurves(Level level, UUID borderId) {
        BorderAPI.CURVE(level).ifPresent(curves -> {
            ensureCurve(curves, borderId, PURPOSE_PLACEMENT, PLACEMENT_SHAPE, PLACEMENT_STEEPNESS);
            ensureCurve(curves, borderId, PURPOSE_DIFFICULTY, DIFFICULTY_SHAPE, DIFFICULTY_STEEPNESS);
        });
    }

    private static void ensureCurve(
            BorderCurveFixture curves, UUID borderId, String purpose, Shape shape, double steepness) {
        Optional<BorderCurve> existing = curves.forBorder(borderId, purpose);
        if (existing.isEmpty()) {
            curves.create(borderId, purpose, shape, steepness);
            return;
        }
        BorderCurve current = existing.get();
        if (current.shape() != shape || current.steepness() != steepness) {
            curves.replace(borderId, purpose, shape, steepness);
        }
    }

    /**
     * The nearest alive, positioned boss with a border to {@code pos}, with its border's radius and
     * its two sentinel curves. Distance is to the boss's own position, not the border's center:
     * boss placement is biased toward the border's outer edge, so a center-measured distance
     * under-reports intensity near the boss.
     */
    static Optional<Nearby> resolveNearby(ServerLevel level, BossFixture bossFixture,
                                          BorderCurveFixture curves, BlockPos pos) {
        Optional<KeeperFixture> keepersOpt = KeeperAPI.keepers(level);
        if (keepersOpt.isEmpty()) {
            return Optional.empty();
        }
        BossRecord nearest = null;
        KeeperRecord nearestKeeper = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (BossRecord r : bossFixture.all()) {
            if (!r.alive() || !r.positioned()) {
                continue;
            }
            // An entry without its Keeper record is not whole, and a Keeper without a border has no curves.
            Optional<KeeperRecord> keeperOpt = keepersOpt.get().get(r.bossId());
            if (keeperOpt.isEmpty() || keeperOpt.get().borderId().isEmpty()) {
                continue;
            }
            double distSq = r.position().distSqr(pos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = r;
                nearestKeeper = keeperOpt.get();
            }
        }
        if (nearest == null) {
            return Optional.empty();
        }

        UUID borderId = nearestKeeper.borderId().get(); // present by the filter above
        Optional<Border> borderOpt = BorderAPI.CRUD(level).flatMap(crud -> crud.get(borderId));
        if (borderOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<BorderCurve> placementOpt = curves.forBorder(borderId, PURPOSE_PLACEMENT);
        Optional<BorderCurve> difficultyOpt = curves.forBorder(borderId, PURPOSE_DIFFICULTY);
        if (placementOpt.isEmpty() || difficultyOpt.isEmpty()) {
            // A curve dropped on load (an unreadable shape) would otherwise stop sentinels for this
            // border until the next boss/border event; re-run the idempotent ensure and retry once.
            ensureSentinelCurves(level, borderId);
            placementOpt = curves.forBorder(borderId, PURPOSE_PLACEMENT);
            difficultyOpt = curves.forBorder(borderId, PURPOSE_DIFFICULTY);
            if (placementOpt.isEmpty() || difficultyOpt.isEmpty()) {
                return Optional.empty();
            }
        }

        return Optional.of(new Nearby(nearest, nearestKeeper, borderOpt.get().radius(), placementOpt.get(),
                difficultyOpt.get(), BorderMath.distanceTo(nearest.position(), pos)));
    }

    /**
     * How many more Sentinels the level may take for a spawn near {@code keeper}, given the {@code sentinelCount}
     * it holds now: 0 when the Keeper's path position is below {@code sentinelFirstPathPosition} or the level is at
     * {@code sentinelMaxPerLevel}, so a result of 1 or more passes both gates.
     */
    static int conversionRoom(KeeperRecord keeper, int sentinelCount) {
        if (KeeperAPI.pathPositionOf(keeper) < SentinelRules.sentinelFirstPathPosition()) {
            return 0;
        }
        return Math.max(0, SentinelRules.sentinelMaxPerLevel() - sentinelCount);
    }

    /**
     * The placement roll: normalized distance is {@code distanceToBoss / (radius x
     * sentinelPlacementRadiusFraction)}, so the ramp is concentrated in a ring around the boss;
     * beyond that fraction the LINEAR curve reads 0 and nothing converts.
     */
    static boolean rollPlacement(Nearby nearby) {
        double placementRadius = nearby.radius() * SentinelRules.sentinelPlacementRadiusFraction();
        double normalized = placementRadius > 0.0 ? nearby.distanceToBoss() / placementRadius : 1.0;
        double intensity = BorderMath.intensityAt(nearby.placement(), normalized);
        if (intensity <= 0.0) {
            return false;
        }
        return RNG.nextDouble() < intensity * SentinelRules.sentinelPlacementCoefficient();
    }

    /**
     * Difficulty reads the full border radius, not the placement fraction: toughness ramps across the whole
     * border.
     */
    static double difficultyIntensity(Nearby nearby) {
        return BorderMath.intensityAt(
                nearby.difficulty(), nearby.radius() > 0.0 ? nearby.distanceToBoss() / nearby.radius() : 1.0);
    }

    /**
     * The largest scale at most {@code min(target, sentinelMaxScale)} whose scaled hitbox fits at the
     * mob's position, stepping down from that ceiling by {@code sentinelFitStep} toward 1.0. Scale 1.0
     * needs no test: a natural spawn's spot has already passed vanilla's fit check for the unscaled mob.
     */
    static float fitScale(ServerLevel level, Mob mob, double target) {
        double ceiling = Math.min(target, SentinelRules.sentinelMaxScale());
        double step = SentinelRules.sentinelFitStep();
        BlockPos pos = mob.blockPosition();
        for (double s = ceiling; s > 1.0 + 1.0E-6; s -= step) {
            if (MobSpawns.fits(level, mob, (float) s, pos)) {
                return (float) s;
            }
        }
        return 1.0F;
    }

    /**
     * How many extra Sentinels the leftover budget {@code L = (target - 1) - (size - 1)} buys at
     * {@code sentinelAddCost} each: an expected count of {@code L / cost}, rolled whole-plus-fractional and clamped
     * to {@code sentinelMaxAddsPerSpawn} and to {@code roomForAdds}, the room left under
     * {@code sentinelMaxPerLevel} after this conversion. 0 when there is nothing left to spend.
     */
    static int addCount(double target, float size, int roomForAdds) {
        double leftover = (target - 1.0) - (size - 1.0);
        double cost = SentinelRules.sentinelAddCost();
        if (leftover <= 0.0 || cost <= 0.0 || roomForAdds <= 0) {
            return 0;
        }
        int cap = Math.min(SentinelRules.sentinelMaxAddsPerSpawn(), roomForAdds);
        return MobSpawns.rollCount(leftover / cost, cap, RNG);
    }

    /** Conversion's treatment: the Sentinel rung of the stat ladder, and the sun ward. */
    static void applyConversion(Mob mob, int keeperPathPosition, double difficultyIntensity) {
        DifficultyAPI.applySentinelRung(mob, keeperPathPosition, difficultyIntensity);
        // Before vanilla's own setup, which only fills empty equipment slots.
        KeeperItems.equipSunWard(mob);
    }
}
