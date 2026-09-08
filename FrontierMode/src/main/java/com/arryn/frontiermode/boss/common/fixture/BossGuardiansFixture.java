package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.Shape;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Third sibling fixture to {@link BossFixture}/{@link BossTellFixture} inside {@link BossBundle},
 * per RM_FRO_029 ("Gloria") / wiki/frontiermode/architecture/guardian-mobs.md.
 *
 * <p>Zero persisted state -- no {@code registerCustom(...)} call, unlike its two siblings. A
 * guardian isn't tracked once it spawns; the guardian-ness lives entirely in the spawned entity's
 * own stats, team, and name tag (see {@code DefaultBossRules.tagGuardian}), the same non-tracking
 * precedent {@link BossFixture} itself already uses. Kept as a real {@code SatchelFixture} anyway,
 * not a static utility, purely for access parity with its siblings -- this fixture reaches
 * {@link BossFixture} through {@code getBundle().boss()}, the same one path
 * {@link BossTellFixture#onJigTick()} already uses, rather than a second, parallel static lookup.
 *
 * <p>Event-driven, not tick-driven: unlike {@link BossTellFixture}, this fixture has no
 * {@code onJigTick()} body -- its one job runs off
 * {@code com.arryn.frontiermode.boss.BossModule#onMobSpawnFinalize}, a raw Forge
 * {@code MobSpawnEvent.FinalizeSpawn} listener, not {@code BOSS_JIG}'s own tick cadence. It still
 * rides inside {@code BossBundle} (and so {@code BOSS_JIG}'s existing tick/pulse wiring, already
 * paid for by its siblings) purely for bundle membership, not because this fixture itself needs a
 * tick.
 */
public final class BossGuardiansFixture extends SatchelFixture {

    private static final String PURPOSE_PLACEMENT = "placement";
    private static final String PURPOSE_DIFFICULTY = "difficulty";

    // 2026-09-07 playtest, revised twice same session. First cut: LINEAR normalized against the
    // full border radius, per border-curve.md's own worked example -- read as "clustered on the
    // boss, nothing past ~30 blocks" on a large border (mathematically correct, just spread too
    // thin to notice). Tried FLAT (uniform everywhere) next -- reverted, "doesn't act as a tell at
    // all," no gradient left to notice. Settled: back to LINEAR (border-curve.md's original shape
    // was right), but the roll's own normalized distance divides by a *fraction* of the border
    // radius (see BossRules#guardianPlacementRadiusFraction()), not the whole thing -- same formula,
    // smaller circle. border-curve.md's worked example still describes plain full-radius LINEAR and
    // is stale against this until the Architect reconciles it.
    private static final Shape PLACEMENT_SHAPE = Shape.LINEAR;
    private static final double PLACEMENT_STEEPNESS = 1.0; // ignored by LINEAR; kept in case a future shape swap wants it
    private static final Shape DIFFICULTY_SHAPE = Shape.LOG;
    private static final double DIFFICULTY_STEEPNESS = 1.0;

    // Shared RNG -- same static-field, not-persisted shape BossTellFixture.RNG already uses.
    private static final RandomSource RNG = RandomSource.create();

    /**
     * Ensures {@code borderId} carries a {@code "placement"} and a {@code "difficulty"}
     * {@link BorderCurve} matching this class's own current {@code PLACEMENT_SHAPE}/
     * {@code DIFFICULTY_SHAPE} constants -- creates either if absent (mirroring
     * {@link BossTellFixture#createTellCurveIfAbsent(Level, UUID)}'s idempotent shape), but also
     * reconciles an existing record whose shape/steepness has drifted from the current constants
     * via {@link BorderCurveFixture#replace}, so a live world that seeded a curve under an older
     * "safe baseline, replace later" value picks up a later tuning pass without a world reset.
     * Called from the same three paired boss-creation call sites
     * {@code createTellCurveIfAbsent} already runs from
     * ({@code BossJigHandlers.onBordersScopeLoaded}/{@code onMobDied}, {@code BossAPI.forceDefeat}).
     */
    public static void ensureGuardianCurves(Level level, UUID borderId) {
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
     * Called from {@code BossModule.onMobSpawnFinalize} for every mob about to finalize-spawn on
     * this fixture's own level. Finds the nearest alive/positioned boss with a border, resolves
     * that border's two Guardian curves, and rolls whether this spawn becomes a guardian --
     * mirrors {@link BossTellFixture#runTellsForPlayers} almost exactly, substituting the spawning
     * mob's position for a player's.
     *
     * <p>Deliberately does nothing (standby, don't crash) if Boss/Border state isn't resolvable,
     * or no boss/curve pair is found -- same discipline as every other early-return branch in this
     * cluster. Never cancels or blocks the spawn either way; this is a modifier on an
     * already-happening spawn, not a summon.
     */
    public void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        Satchel.requireServer();
        BossBundle bundle = getBundle();
        bundle.boss().ifPresent(bossFixture ->
                BorderAPI.CURVE(level).ifPresent(curves -> tryGuardianize(mob, level, bossFixture, curves)));
    }

    private void tryGuardianize(Mob mob, ServerLevel level, BossFixture bossFixture, BorderCurveFixture curves) {
        BlockPos spawnPos = mob.blockPosition();

        // Alive+positioned bosses with a borderId are the only candidates -- identical filter
        // BossTellFixture.runTellsForPlayers already establishes.
        List<BossRecord> candidates = bossFixture.all().stream()
                .filter(r -> r.alive() && r.positioned() && r.borderId().isPresent())
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        // Nearest alive+positioned boss to this spawn -- same manual nearest-distance scan
        // runTellsForPlayers uses per player, substituting the spawning mob's own position.
        BossRecord nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (BossRecord r : candidates) {
            double distSq = r.position().distSqr(spawnPos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = r;
            }
        }
        if (nearest == null) {
            return;
        }

        UUID borderId = nearest.borderId().get(); // present by construction of `candidates`

        Optional<Border> borderOpt = BorderAPI.CRUD(level).flatMap(crud -> crud.get(borderId));
        if (borderOpt.isEmpty()) {
            return;
        }
        Optional<BorderCurve> placementOpt = curves.forBorder(borderId, PURPOSE_PLACEMENT);
        Optional<BorderCurve> difficultyOpt = curves.forBorder(borderId, PURPOSE_DIFFICULTY);
        if (placementOpt.isEmpty() || difficultyOpt.isEmpty()) {
            // Self-heal rather than staying stuck: a curve can go missing here even outside the
            // three lifecycle events ensureGuardianCurves normally runs from (border scope load,
            // boss death, force-defeat). Concretely, this run's own Shape.FLAT revert is exactly
            // such a case -- a border whose "placement" curve was persisted under the
            // now-removed Shape.FLAT fails Shape.valueOf(...) on load, and
            // BorderCurveFixture.loadCurves catches that per-record (same "one bad record
            // shouldn't abort the rest" discipline BossFixture/BordersFixture already use) and
            // silently drops just that one curve -- leaving this border with no "placement"
            // curve at all until one of those three lifecycle events happens to fire again. For
            // an already-running boss that predates a rebuild, that could be "never, this
            // playtest" -- which reads in-game as guardians having stopped spawning entirely,
            // not as a curve gap. Re-run the same idempotent ensure/reconcile helper right here
            // and retry the lookup once before giving up.
            ensureGuardianCurves(level, borderId);
            placementOpt = curves.forBorder(borderId, PURPOSE_PLACEMENT);
            difficultyOpt = curves.forBorder(borderId, PURPOSE_DIFFICULTY);
            if (placementOpt.isEmpty() || difficultyOpt.isEmpty()) {
                return;
            }
        }

        // Distance to the boss' own resolved position, not the border's geometric center --
        // inherited directly from BossTellFixture's own 2026-09-06 bugfix (FRO_072 biases boss
        // placement toward its border's outer edge, so measuring from center under-reports
        // intensity near a boss that isn't centered).
        double distanceToBoss = BorderMath.distanceTo(nearest.position(), spawnPos);
        double radius = borderOpt.get().radius();

        // Placement's own normalized distance divides by a *fraction* of the border radius, not
        // the whole thing -- see guardianPlacementRadiusFraction()'s own doc for why. Clamped to
        // [0,1] inside BorderMath.intensityAt same as every other normalized distance in this
        // cluster, so anything beyond that fraction reads as the curve's own edge value (0, for
        // LINEAR) -- no guardians out there at all, by construction.
        double placementRadius = radius * bossFixture.RULES.guardianPlacementRadiusFraction();
        double placementNormalizedDistance = placementRadius > 0.0 ? distanceToBoss / placementRadius : 1.0;
        double placementIntensity = BorderMath.intensityAt(placementOpt.get(), placementNormalizedDistance);
        if (placementIntensity <= 0.0) {
            return;
        }

        double coefficient = bossFixture.RULES.guardianPlacementCoefficient();
        if (RNG.nextDouble() >= placementIntensity * coefficient) {
            // Miss -- vanilla spawn proceeds unmodified, no guardian roll retried.
            return;
        }

        // Difficulty (stat scaling) is unaffected by the placement-radius fraction above -- it
        // still reads off the boss's own full border radius, unchanged from the original build.
        // Nothing in this playtest round asked for guardian toughness to compress into a smaller
        // ring, only for how often/far out they appear at all.
        double difficultyNormalizedDistance = distanceToBoss / radius;
        double difficultyIntensity = BorderMath.intensityAt(difficultyOpt.get(), difficultyNormalizedDistance);
        int tier = bossFixture.RULES.guardianTier(difficultyIntensity);
        bossFixture.RULES.tagGuardian(mob, tier, difficultyIntensity, level);
        bossFixture.RULES.applyGuardianStatScaling(mob, difficultyIntensity);
    }
}
