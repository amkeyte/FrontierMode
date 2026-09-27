package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.Shape;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Third sibling fixture to {@link BossFixture}/{@link BossTellFixture} inside {@link BossBundle},
 * per RM_FRO_029 ("Gloria") / wiki/frontiermode/architecture/guardian-mobs.md.
 *
 * <p>A real, persisted {@code SatchelFixture}: it owns the guardian records, one per guardian mob
 * keyed by its UUID -- see {@link GuardianRecord}. Conversion runs at spawn finalization, before
 * the mob is in the world and before a finalized spawn is known to succeed, so it <em>stages</em> a
 * record in memory; the mob's fresh join promotes the staged entry to a record
 * ({@link #promote}). This fixture's tick clears whatever is still staged, and the lifted spawn
 * positions, so a record exists only for a guardian that entered the world.
 *
 * <p>Reaches {@link BossFixture} through {@code getBundle().boss()}, the same one path
 * {@link BossTellFixture#onJigTick()} uses, rather than a second, parallel static lookup.
 *
 * <p>Conversion is event-driven ({@code BossModule.onMobSpawnFinalize}); the fixture rides
 * {@code BOSS_JIG}'s tick and execution pulse for the rest.
 */
public final class BossGuardiansFixture extends SatchelFixture {

    /**
     * One guardian. {@code bossId} never leaves the server; {@code scale} is fixed at conversion.
     * {@code doomed} is set when its boss is defeated: the guardian dies with it, and one that wasn't
     * present at that moment is removed the moment it next loads. New per-guardian facts are added
     * here as fields.
     */
    public record GuardianRecord(UUID mobUuid, UUID bossId, float scale, boolean doomed) {
    }

    private static final String KEY_GUARDIANS = "guardians";

    // Concurrent so the MobJig interest walk can iterate the live key view while a handler it
    // triggers deletes a record.
    private final Map<UUID, GuardianRecord> records = new ConcurrentHashMap<>();
    private final Set<UUID> recordIds = Collections.unmodifiableSet(records.keySet());

    // Transient, never persisted: conversion's staged entries and the spawn positions where the
    // darkness gate was lifted (see the daylight rule). Both are cleared on the tick.
    private final Map<UUID, GuardianRecord> staged = new HashMap<>();
    private final Set<Long> liftedPositions = new HashSet<>();

    /** An extra guardian queued at conversion, spawned on this fixture's next tick. */
    private record PendingAdd(EntityType<?> type, UUID parentUuid, UUID bossId, float scale,
                              double difficultyIntensity) {
    }

    // Transient. Spawned on the tick, never inside the finalize-spawn dispatch that queued them.
    private final List<PendingAdd> pendingAdds = new ArrayList<>();

    /** A doomed guardian queued for its staggered kill, and the player credited with it (null: uncredited). */
    private record PendingKill(UUID mobUuid, UUID creditedPlayer) {
    }

    // Transient: doomed on the record is what survives a restart or an unload before a guardian's turn.
    private final Deque<PendingKill> pendingKills = new ArrayDeque<>();

    // Candidate spots MobSpawns.scatter tries for one extra guardian.
    private static final int ADD_SCATTER_TRIES = 4;

    public BossGuardiansFixture() {
        registerCustom(KEY_GUARDIANS, this::saveRecords, this::loadRecords);
    }

    private void saveRecords(CompoundTag root) {
        ListTag list = new ListTag();
        for (GuardianRecord r : records.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("mobUuid", r.mobUuid);
            tag.putUUID("bossId", r.bossId);
            tag.putFloat("scale", r.scale);
            tag.putBoolean("doomed", r.doomed);
            list.add(tag);
        }
        root.put(KEY_GUARDIANS, list);
    }

    private void loadRecords(CompoundTag root) {
        records.clear();
        if (!root.contains(KEY_GUARDIANS, Tag.TAG_LIST)) {
            return;
        }
        for (Tag t : root.getList(KEY_GUARDIANS, Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) t;
            if (!tag.hasUUID("mobUuid") || !tag.hasUUID("bossId")) {
                continue;
            }
            UUID mobUuid = tag.getUUID("mobUuid");
            records.put(mobUuid, new GuardianRecord(
                    mobUuid, tag.getUUID("bossId"), tag.getFloat("scale"), tag.getBoolean("doomed")));
        }
    }

    // ── records ──────────────────────────────────────────────────────────────

    /**
     * A live, read-only view of every record's mob UUID -- the {@code GUARDIAN_MOB_JIG} interest
     * set. A view, not a copy: deleting a record stops tracking it, and the interest gate that runs
     * on every mob death never rebuilds anything.
     */
    public Set<UUID> recordIds() {
        return recordIds;
    }

    public Collection<GuardianRecord> all() {
        return records.values();
    }

    public Optional<GuardianRecord> record(UUID mobUuid) {
        return Optional.ofNullable(records.get(mobUuid));
    }

    /** True if {@code mobUuid} has a record or a staged entry. */
    public boolean isGuardian(UUID mobUuid) {
        return records.containsKey(mobUuid) || staged.containsKey(mobUuid);
    }

    /**
     * The guardian's size multiplier from its record or staged entry, or {@code 1.0} for a mob that
     * is neither. Staged entries count so a guardian enters the world already at its guardian size.
     */
    public float scaleOf(UUID mobUuid) {
        GuardianRecord r = records.get(mobUuid);
        if (r == null) {
            r = staged.get(mobUuid);
        }
        return r == null ? 1.0F : r.scale;
    }

    /** Promotes the staged entry for {@code mobUuid} to a record; empty if nothing was staged. */
    public Optional<GuardianRecord> promote(UUID mobUuid) {
        GuardianRecord r = staged.remove(mobUuid);
        if (r == null) {
            return Optional.empty();
        }
        records.put(mobUuid, r);
        markDirty();
        return Optional.of(r);
    }

    /** Deletes the record for {@code mobUuid}; empty if it had none. */
    public Optional<GuardianRecord> delete(UUID mobUuid) {
        GuardianRecord r = records.remove(mobUuid);
        if (r != null) {
            markDirty();
        }
        return Optional.ofNullable(r);
    }

    /**
     * The boss {@code bossId} was defeated: marks every one of its guardian records doomed and queues
     * them for a staggered kill on this fixture's tick. {@code creditedPlayer} is the player who
     * defeated it, so experience and player-only drops follow; {@code null} (a forced defeat) kills
     * uncredited. Only this boss's own guardians, whether or not its siblings still stand.
     */
    public void doomGuardiansOf(UUID bossId, UUID creditedPlayer) {
        boolean changed = false;
        for (GuardianRecord r : records.values()) {
            if (r.bossId.equals(bossId) && !r.doomed) {
                records.put(r.mobUuid, new GuardianRecord(r.mobUuid, r.bossId, r.scale, true));
                pendingKills.add(new PendingKill(r.mobUuid, creditedPlayer));
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    private void stage(UUID mobUuid, UUID bossId, float scale) {
        staged.put(mobUuid, new GuardianRecord(mobUuid, bossId, scale, false));
    }

    // ── lifted spawn positions ───────────────────────────────────────────────

    /** Records a position where the darkness gate was lifted for a spawn that will become a guardian. */
    public void markLifted(BlockPos pos) {
        liftedPositions.add(pos.asLong());
    }

    /** True if {@code pos} is a recorded lifted position. Read only: finalize-spawn consumes the entry. */
    public boolean isLifted(BlockPos pos) {
        return liftedPositions.contains(pos.asLong());
    }

    private boolean consumeLifted(BlockPos pos) {
        return liftedPositions.remove(pos.asLong());
    }

    /**
     * Spawns the queued extra guardians, then clears whatever is still staged and the lifted spawn
     * positions. Finalization and the join happen in one tick, so a record exists only for a
     * guardian that actually entered the world.
     */
    @Override
    public void onJigTick() {
        spawnPendingAdds();
        killDoomed();
        staged.clear();
        liftedPositions.clear();
    }

    /**
     * Kills up to {@code guardianDefeatKillsPerTick} of the queued doomed guardians that are present.
     * One that isn't present (unloaded) is skipped and costs nothing: its record stays doomed, and it
     * is removed when it next loads.
     */
    private void killDoomed() {
        if (pendingKills.isEmpty()) {
            return;
        }
        BossBundle bundle = getBundle();
        ServerLevel level = (ServerLevel) bundle.level();
        Optional<BossFixture> bossOpt = bundle.boss();
        if (bossOpt.isEmpty()) {
            return;
        }
        BossFixture boss = bossOpt.get();
        int budget = boss.RULES.guardianDefeatKillsPerTick();
        while (budget > 0 && !pendingKills.isEmpty()) {
            PendingKill kill = pendingKills.poll();
            Optional<Entity> entity = Satchel.require().egress().getEntity(level, kill.mobUuid);
            if (entity.isEmpty() || !(entity.get() instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            // A credited player who has left the level by now: the guardian dies uncredited.
            ServerPlayer credited = kill.creditedPlayer != null
                    && level.getPlayerByUUID(kill.creditedPlayer) instanceof ServerPlayer player ? player : null;
            boss.RULES.killGuardian(mob, credited);
            budget--;
        }
    }

    private void spawnPendingAdds() {
        if (pendingAdds.isEmpty()) {
            return;
        }
        List<PendingAdd> adds = List.copyOf(pendingAdds);
        pendingAdds.clear();

        BossBundle bundle = getBundle();
        ServerLevel level = (ServerLevel) bundle.level();
        Optional<BossFixture> bossOpt = bundle.boss();
        if (bossOpt.isEmpty()) {
            return;
        }
        BossFixture boss = bossOpt.get();
        for (PendingAdd add : adds) {
            // Only if the parent actually joined the level: a parent that never made it in brings
            // no adds. Resolved through Satchel's egress, the lookup MobJig's poll uses.
            GuardianRecord parentRecord = records.get(add.parentUuid);
            if (parentRecord == null || parentRecord.doomed) {
                continue;
            }
            Optional<Entity> parent = Satchel.require().egress().getEntity(level, add.parentUuid);
            if (parent.isEmpty() || !(parent.get() instanceof Mob parentMob)) {
                continue;
            }
            spawnAdd(level, boss, parentMob, add);
        }
    }

    /**
     * One extra guardian: the parent's own type and size, scattered near the parent, with
     * {@code VANILLA} setup so it never re-enters the spawn fan-out (never rolled for conversion,
     * never spawns adds of its own). Conversion's treatment is applied in {@code preSetup}; its
     * join promotes it to a record like any guardian's.
     */
    private void spawnAdd(ServerLevel level, BossFixture boss, Mob parent, PendingAdd add) {
        BlockPos spot = MobSpawns.scatter(level, parent, parent.blockPosition(),
                boss.RULES.guardianAddScatterRadius(), add.scale, ADD_SCATTER_TRIES);
        MobSpawns.spawn(level, add.type, spot, MobSpawns.Setup.VANILLA, mob -> {
            stage(mob.getUUID(), add.bossId, add.scale);
            applyConversion(mob, level, boss, add.difficultyIntensity);
            mob.refreshDimensions();
        });
    }

    /** Conversion's treatment: stat scaling, the sun ward, the guardian team. */
    private void applyConversion(Mob mob, ServerLevel level, BossFixture boss, double difficultyIntensity) {
        boss.RULES.applyGuardianStatScaling(mob, difficultyIntensity);
        // Before vanilla's own setup, which only fills empty equipment slots.
        boss.RULES.equipSunWard(mob);
        boss.RULES.joinGuardianTeam(mob, level);
    }

    private static final String PURPOSE_PLACEMENT = "placement";
    private static final String PURPOSE_DIFFICULTY = "difficulty";

    // Placement is LINEAR over a fraction of the border radius (see guardianPlacementRadiusFraction).
    private static final Shape PLACEMENT_SHAPE = Shape.LINEAR;
    private static final double PLACEMENT_STEEPNESS = 1.0; // ignored by LINEAR
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

    /** What one spawn position resolves to: its nearest boss, that boss's border, and the two curves. */
    private record Nearby(BossRecord boss, double radius, BorderCurve placement, BorderCurve difficulty,
                          double distanceToBoss) {
    }

    /** {@code BossRules#canBeGuardian} through this level's boss rules; false if they aren't resolvable yet. */
    public boolean canBeGuardian(EntityType<?> type) {
        BossBundle bundle = getBundle();
        return bundle.boss().map(boss -> boss.RULES.canBeGuardian(type)).orElse(false);
    }

    /**
     * Called from {@code BossModule.onMobSpawnFinalize} for every mob about to finalize-spawn on
     * this fixture's own level. Rolls whether this spawn becomes a guardian; on a hit, converts it in
     * place. Never cancels or blocks the spawn -- this is a modifier on an already-happening spawn,
     * not a summon. Standby, don't crash: nothing resolvable means the spawn proceeds unmodified.
     */
    public void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        if (!canBeGuardian(mob.getType())) {
            return;
        }

        Satchel.requireServer();
        // A lifted spawn already rolled at the placement gate; consuming here also retires the entry.
        boolean lifted = consumeLifted(mob.blockPosition());
        BossBundle bundle = getBundle();
        bundle.boss().ifPresent(bossFixture ->
                BorderAPI.CURVE(level).ifPresent(curves ->
                        tryMakeGuardian(mob, level, bossFixture, curves, lifted)));
    }

    private void tryMakeGuardian(Mob mob, ServerLevel level, BossFixture bossFixture,
                                 BorderCurveFixture curves, boolean lifted) {
        Optional<Nearby> nearbyOpt = resolveNearby(level, bossFixture, curves, mob.blockPosition());
        if (nearbyOpt.isEmpty()) {
            return;
        }
        Nearby nearby = nearbyOpt.get();

        if (!lifted && !rollPlacement(bossFixture, nearby)) {
            return;
        }

        // Difficulty reads the full border radius, not the placement fraction: toughness ramps
        // across the whole border.
        double difficultyIntensity = BorderMath.intensityAt(
                nearby.difficulty(), nearby.radius() > 0.0 ? nearby.distanceToBoss() / nearby.radius() : 1.0);
        double target = bossFixture.RULES.guardianTargetScale(nearby.boss().layer());
        float size = fitScale(level, mob, target, bossFixture);

        applyConversion(mob, level, bossFixture, difficultyIntensity);
        stage(mob.getUUID(), nearby.boss().bossId(), size);
        if (size != 1.0F) {
            // The mob was built at full size before it was known to be a guardian; the staged
            // entry is what the Size handler reads, so refresh now and it enters the world scaled.
            mob.refreshDimensions();
        }

        queueAdds(mob, nearby.boss().bossId(), size, target, difficultyIntensity, bossFixture);
    }

    /**
     * The largest scale at most {@code min(target, guardianMaxScale)} whose scaled hitbox fits at the
     * mob's position, stepping down from that ceiling by {@code guardianFitStep} toward 1.0. Scale 1.0
     * needs no test: a natural spawn's spot has already passed vanilla's fit check for the unscaled mob.
     */
    private static float fitScale(ServerLevel level, Mob mob, double target, BossFixture boss) {
        double ceiling = Math.min(target, boss.RULES.guardianMaxScale());
        double step = boss.RULES.guardianFitStep();
        BlockPos pos = mob.blockPosition();
        for (double s = ceiling; s > 1.0 + 1.0E-6; s -= step) {
            if (MobSpawns.fits(level, mob, (float) s, pos)) {
                return (float) s;
            }
        }
        return 1.0F;
    }

    /**
     * The leftover budget {@code L = (target - 1) - (size - 1)} buys extra guardians at
     * {@code guardianAddCost} each: an expected count of {@code L / cost}, rolled whole-plus-
     * fractional and clamped to {@code guardianMaxAddsPerSpawn}. Queued, not spawned: never inside
     * the finalize-spawn dispatch that produced them.
     */
    private void queueAdds(Mob parent, UUID bossId, float size, double target, double difficultyIntensity,
                           BossFixture boss) {
        double leftover = (target - 1.0) - (size - 1.0);
        double cost = boss.RULES.guardianAddCost();
        if (leftover <= 0.0 || cost <= 0.0) {
            return;
        }
        int count = MobSpawns.rollCount(leftover / cost, boss.RULES.guardianMaxAddsPerSpawn(), RNG);
        for (int i = 0; i < count; i++) {
            pendingAdds.add(new PendingAdd(parent.getType(), parent.getUUID(), bossId, size, difficultyIntensity));
        }
    }

    /**
     * The placement roll for a spawn that hasn't happened yet: true if {@code pos} is inside the
     * nearest boss's placement radius and the roll hits. Standby, don't crash: nothing resolvable
     * means no.
     */
    public boolean rollPlacementAt(ServerLevel level, BlockPos pos) {
        BossBundle bundle = getBundle();
        Optional<BossFixture> bossOpt = bundle.boss();
        Optional<BorderCurveFixture> curvesOpt = BorderAPI.CURVE(level);
        if (bossOpt.isEmpty() || curvesOpt.isEmpty()) {
            return false;
        }
        return resolveNearby(level, bossOpt.get(), curvesOpt.get(), pos)
                .map(nearby -> rollPlacement(bossOpt.get(), nearby))
                .orElse(false);
    }

    /**
     * The nearest alive, positioned boss with a border to {@code pos}, with its border's radius and
     * its two guardian curves. Distance is to the boss's own position, not the border's center:
     * boss placement is biased toward the border's outer edge, so a center-measured distance
     * under-reports intensity near the boss.
     */
    private Optional<Nearby> resolveNearby(ServerLevel level, BossFixture bossFixture,
                                           BorderCurveFixture curves, BlockPos pos) {
        BossRecord nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (BossRecord r : bossFixture.all()) {
            if (!r.alive() || !r.positioned() || r.borderId().isEmpty()) {
                continue;
            }
            double distSq = r.position().distSqr(pos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = r;
            }
        }
        if (nearest == null) {
            return Optional.empty();
        }

        UUID borderId = nearest.borderId().get(); // present by the filter above
        Optional<Border> borderOpt = BorderAPI.CRUD(level).flatMap(crud -> crud.get(borderId));
        if (borderOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<BorderCurve> placementOpt = curves.forBorder(borderId, PURPOSE_PLACEMENT);
        Optional<BorderCurve> difficultyOpt = curves.forBorder(borderId, PURPOSE_DIFFICULTY);
        if (placementOpt.isEmpty() || difficultyOpt.isEmpty()) {
            // A curve dropped on load (an unreadable shape) would otherwise stop guardians for this
            // border until the next boss/border event; re-run the idempotent ensure and retry once.
            ensureGuardianCurves(level, borderId);
            placementOpt = curves.forBorder(borderId, PURPOSE_PLACEMENT);
            difficultyOpt = curves.forBorder(borderId, PURPOSE_DIFFICULTY);
            if (placementOpt.isEmpty() || difficultyOpt.isEmpty()) {
                return Optional.empty();
            }
        }

        return Optional.of(new Nearby(nearest, borderOpt.get().radius(), placementOpt.get(),
                difficultyOpt.get(), BorderMath.distanceTo(nearest.position(), pos)));
    }

    /**
     * The placement roll: normalized distance is {@code distanceToBoss / (radius x
     * guardianPlacementRadiusFraction)}, so the ramp is concentrated in a ring around the boss;
     * beyond that fraction the LINEAR curve reads 0 and nothing converts.
     */
    private boolean rollPlacement(BossFixture bossFixture, Nearby nearby) {
        double placementRadius = nearby.radius() * bossFixture.RULES.guardianPlacementRadiusFraction();
        double normalized = placementRadius > 0.0 ? nearby.distanceToBoss() / placementRadius : 1.0;
        double intensity = BorderMath.intensityAt(nearby.placement(), normalized);
        if (intensity <= 0.0) {
            return false;
        }
        return RNG.nextDouble() < intensity * bossFixture.RULES.guardianPlacementCoefficient();
    }
}
