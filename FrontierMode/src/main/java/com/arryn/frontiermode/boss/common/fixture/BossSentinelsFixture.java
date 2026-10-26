package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.Shape;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.SentinelData;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.core.Presence;
import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Third sibling fixture to {@link BossFixture}/{@link BossTellFixture} inside {@link BossBundle},
 * per RM_FRO_029 ("Gloria") / wiki/frontiermode/architecture/sentinels.md.
 *
 * <p>It persists nothing. A Sentinel is a record on the mob itself ({@link SentinelData}), so no record
 * outside the mob can go stale. What this fixture holds is runtime state: the Sentinels present in the
 * level ({@link Presence}), the extra Sentinels queued at conversion, the spawn positions where the
 * darkness gate was lifted, the alert windows and the defeat kill queue. It rides {@code BOSS_JIG}'s
 * tick, and moves into a Trinket with the sentinel module.
 *
 * <p>Conversion runs at spawn finalization, before the mob is in the world: it writes the record onto
 * the mob. The mob's join queues it in presence, and this fixture's tick adopts it.
 *
 * <p>Reaches {@link BossFixture} through {@code getBundle().boss()}, the same one path
 * {@link BossTellFixture#onJigTick()} uses, rather than a second, parallel static lookup.
 *
 * <p>Conversion is event-driven ({@code BossModule.onMobSpawnFinalize}).
 */
public final class BossSentinelsFixture extends SatchelFixture {

    // Transient: the spawn positions where the darkness gate was lifted (see the daylight rule),
    // cleared on the tick.
    private final Set<Long> liftedPositions = new HashSet<>();

    /** The Sentinels in the level now, and the joins waiting to be adopted. */
    private final Presence present = new Presence();

    // Whether the one-time sweep for Sentinels that joined before this fixture existed has run.
    private boolean swept;

    private final Presence.Hooks presenceHooks = new Presence.Hooks() {
        @Override
        public boolean adopt(Mob mob, boolean fresh) {
            return adoptSentinel(mob, fresh);
        }

        @Override
        public void onDrop(Mob mob) {
            BossAPI.sentinelMarkers(mob.level()).ifPresent(pub -> pub.remove(mob.getUUID()));
        }
    };

    /** An extra sentinel queued at conversion, spawned on this fixture's next tick. */
    private record PendingAdd(EntityType<?> type, UUID parentUuid, UUID bossId, float scale,
                              int keeperPathPosition, double difficultyIntensity) {
    }

    // Transient. Spawned on the tick, never inside the finalize-spawn dispatch that queued them.
    private final List<PendingAdd> pendingAdds = new ArrayList<>();

    /** A sentinel of a defeated boss queued for its staggered kill, and the player credited with it (null: uncredited). */
    private record PendingKill(UUID mobUuid, UUID creditedPlayer) {
    }

    private final Deque<PendingKill> pendingKills = new ArrayDeque<>();

    /** A sentinel turned on {@code playerUuid} by a hit on its boss, held until game time {@code alertedUntil}. */
    private record Alert(UUID playerUuid, long alertedUntil) {
    }

    // Transient, never persisted -- vanilla doesn't save a mob's target either. Sentinel UUID -> its alert.
    private final Map<UUID, Alert> alerts = new HashMap<>();

    // Vertical half-height of the alert box: HurtByTargetGoal.alertOthers's own.
    private static final double ALERT_HEIGHT = 10.0;

    // Candidate spots MobSpawns.scatter tries for one extra sentinel.
    private static final int ADD_SCATTER_TRIES = 4;

    // ── presence ─────────────────────────────────────────────────────────────

    /**
     * A mob that carries the Sentinel record has joined the level, fresh or loaded from disk. It is
     * queued, and this fixture's tick adopts it.
     */
    public void join(Mob mob, boolean fresh) {
        present.join(mob, fresh);
    }

    /** The Sentinels in the level now. A live view; do not change presence while iterating. */
    public Collection<Mob> presentSentinels() {
        return present.mobs();
    }

    /**
     * Adoption, for a queued Sentinel that passed the same-object check. If its boss is defeated or has
     * no record in this level, the Sentinel is dismissed (no drops, no XP) and left out: that covers one
     * that joined after its boss fell, and one that arrived through a portal. Otherwise it is published
     * to clients, its dimensions are refreshed (a reload never recomputes a hitbox), and a fresh one is
     * healed to full, because vanilla's setup can raise max health without healing. A reloaded Sentinel
     * keeps the health it saved with.
     */
    private boolean adoptSentinel(Mob mob, boolean fresh) {
        Optional<SentinelData.Facts> facts = SentinelData.read(mob);
        BossBundle bundle = getBundle();
        Optional<BossFixture> bossOpt = bundle.boss();
        if (facts.isEmpty() || bossOpt.isEmpty()) {
            return false;
        }
        BossFixture boss = bossOpt.get();
        boolean bossStands = boss.CRUD.get(facts.get().bossId()).map(BossRecord::alive).orElse(false);
        if (!bossStands) {
            boss.RULES.dismiss(mob);
            return false;
        }
        BossAPI.sentinelMarkers(mob.level()).ifPresent(pub -> pub.add(mob.getUUID(), facts.get().scale()));
        mob.refreshDimensions();
        if (fresh) {
            boss.RULES.healSentinel(mob);
        }
        return true;
    }

    // ── defeat ───────────────────────────────────────────────────────────────

    /**
     * The boss {@code bossId} was defeated: queues every present sentinel that names it for a staggered
     * kill on this fixture's tick. {@code creditedPlayer} is the player who defeated it, so experience
     * and player-only drops follow; {@code null} (a forced defeat) kills uncredited. Only this boss's
     * own sentinels, whether or not its siblings still stand. One that joins later is dismissed at
     * adoption.
     */
    public void doomSentinelsOf(UUID bossId, UUID creditedPlayer) {
        for (Mob mob : present.mobs()) {
            if (isSentinelOf(mob, bossId) && !queuedForKill(mob.getUUID())) {
                pendingKills.add(new PendingKill(mob.getUUID(), creditedPlayer));
            }
        }
    }

    private boolean queuedForKill(UUID mobUuid) {
        return pendingKills.stream().anyMatch(kill -> kill.mobUuid.equals(mobUuid));
    }

    private static boolean isSentinelOf(Mob mob, UUID bossId) {
        return SentinelData.read(mob).filter(facts -> facts.bossId().equals(bossId)).isPresent();
    }

    // ── defending the boss ───────────────────────────────────────────────────

    /**
     * {@code player} just hit the boss {@code bossId} (entity {@code boss}): its sentinels in a box
     * around it turn on the player, and each one's alert window opens or is pushed out to
     * {@code sentinelAlertMemoryTicks} from now. Sentinels of any other boss don't join in. See
     * sentinels.md § Defending the boss.
     */
    public void alertSentinelsOf(UUID bossId, Mob boss, ServerPlayer player) {
        BossBundle bundle = getBundle();
        Optional<BossFixture> bossOpt = bundle.boss();
        if (bossOpt.isEmpty()) {
            return;
        }
        BossFixture bossFixture = bossOpt.get();
        ServerLevel level = (ServerLevel) bundle.level();
        double radius = bossFixture.RULES.sentinelAlertRadius();
        long alertedUntil = level.getGameTime() + bossFixture.RULES.sentinelAlertMemoryTicks();
        AABB box = AABB.unitCubeFromLowerCorner(boss.position()).inflate(radius, ALERT_HEIGHT, radius);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (!present.contains(mob.getUUID()) || !isSentinelOf(mob, bossId)) {
                continue;
            }
            if (bossFixture.RULES.alertSentinel(mob, player)) {
                alerts.put(mob.getUUID(), new Alert(player.getUUID(), alertedUntil));
            }
        }
    }

    /**
     * Retires lapsed alerts. A sentinel whose window has run out is let go through
     * {@code BossRules#releaseSentinel}, which keeps it on the player only if it can see them. An
     * entry whose sentinel is gone, unloaded or dead, or whose player has logged out, is simply
     * dropped.
     */
    public void releaseLapsedAlerts(ServerLevel level) {
        if (alerts.isEmpty()) {
            return;
        }
        BossBundle bundle = getBundle();
        Optional<BossFixture> bossOpt = bundle.boss();
        if (bossOpt.isEmpty()) {
            return;
        }
        BossFixture boss = bossOpt.get();
        long now = level.getGameTime();

        Iterator<Map.Entry<UUID, Alert>> it = alerts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Alert> entry = it.next();
            Optional<Mob> entity = present.get(entry.getKey());
            if (entity.isEmpty() || !entity.get().isAlive()) {
                it.remove();
                continue;
            }
            Mob sentinel = entity.get();
            Alert alert = entry.getValue();
            if (now < alert.alertedUntil) {
                continue;
            }
            it.remove();
            // Server-wide, not this level: a player who took a portal is revived as the same object in
            // another level, still alive, so the sentinel keeps chasing unless released. Only a player
            // who logged out (null) needs no call -- vanilla drops a removed target itself.
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(alert.playerUuid);
            if (player != null) {
                boss.RULES.releaseSentinel(sentinel, player);
            }
        }
    }

    // ── lifted spawn positions ───────────────────────────────────────────────

    /** Records a position where the darkness gate was lifted for a spawn that will become a sentinel. */
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
     * Adopts the Sentinels that joined, spawns the queued extra sentinels, runs the defeat kills, then
     * clears the lifted spawn positions. Presence goes first so a parent that joined this tick is
     * present when its adds spawn.
     */
    @Override
    public void onJigTick() {
        BossBundle bundle = getBundle();
        ServerLevel level = (ServerLevel) bundle.level();
        if (!swept) {
            swept = true;
            joinSentinelsAlreadyInLevel(level);
        }
        present.tick(level, presenceHooks);
        spawnPendingAdds();
        killDoomed();
        liftedPositions.clear();
    }

    /**
     * Once, on this fixture's first tick: queues the Sentinels already in the level. A Sentinel in a
     * chunk loaded before this level's scope was ready joined while there was no fixture to take it, so
     * its join never reached {@link #join}.
     */
    private void joinSentinelsAlreadyInLevel(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && SentinelData.has(mob)) {
                present.join(mob, false);
            }
        }
    }

    /**
     * Kills up to {@code sentinelDefeatKillsPerTick} of the queued sentinels that are present. One that
     * is no longer present (unloaded) is skipped and costs nothing: it is dismissed when it next loads,
     * because its boss is defeated.
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
        int budget = boss.RULES.sentinelDefeatKillsPerTick();
        while (budget > 0 && !pendingKills.isEmpty()) {
            PendingKill kill = pendingKills.poll();
            Optional<Entity> entity = Satchel.require().egress().getEntity(level, kill.mobUuid);
            if (entity.isEmpty() || !(entity.get() instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            // A credited player who has left the level by now: the sentinel dies uncredited.
            ServerPlayer credited = kill.creditedPlayer != null
                    && level.getPlayerByUUID(kill.creditedPlayer) instanceof ServerPlayer player ? player : null;
            boss.RULES.killSentinel(mob, credited);
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
            // no adds. Presence has checked that the parent is the same object the level holds.
            Optional<Mob> parent = present.get(add.parentUuid);
            if (parent.isEmpty()) {
                continue;
            }
            spawnAdd(level, boss, parent.get(), add);
        }
    }

    /**
     * One extra sentinel: the parent's own type and size, scattered near the parent, with
     * {@code VANILLA} setup so it never re-enters the spawn fan-out (never rolled for conversion,
     * never spawns adds of its own). Conversion's treatment is applied in {@code preSetup}; its
     * join queues it in presence like any sentinel's.
     */
    private void spawnAdd(ServerLevel level, BossFixture boss, Mob parent, PendingAdd add) {
        BlockPos spot = MobSpawns.scatter(level, parent, parent.blockPosition(),
                boss.RULES.sentinelAddScatterRadius(), add.scale, ADD_SCATTER_TRIES);
        MobSpawns.spawn(level, add.type, spot, MobSpawns.Setup.VANILLA, mob -> {
            SentinelData.writeIfAbsent(mob, add.bossId, add.scale);
            applyConversion(mob, boss, add.keeperPathPosition, add.difficultyIntensity);
            mob.refreshDimensions();
        });
    }

    /** Conversion's treatment: the Sentinel rung of the stat ladder, and the sun ward. */
    private void applyConversion(Mob mob, BossFixture boss, int keeperPathPosition, double difficultyIntensity) {
        DifficultyAPI.applySentinelRung(mob, keeperPathPosition, difficultyIntensity);
        // Before vanilla's own setup, which only fills empty equipment slots.
        boss.RULES.equipSunWard(mob);
    }

    private static final String PURPOSE_PLACEMENT = "placement";
    private static final String PURPOSE_DIFFICULTY = "difficulty";

    // Placement is LINEAR over a fraction of the border radius (see sentinelPlacementRadiusFraction).
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
    public static void ensureSentinelCurves(Level level, UUID borderId) {
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

    /** {@code BossRules#canBeSentinel} through this level's boss rules; false if they aren't resolvable yet. */
    public boolean canBeSentinel(EntityType<?> type) {
        BossBundle bundle = getBundle();
        return bundle.boss().map(boss -> boss.RULES.canBeSentinel(type)).orElse(false);
    }

    /**
     * Called from {@code BossModule.onMobSpawnFinalize} for every mob about to finalize-spawn on
     * this fixture's own level. Rolls whether this spawn becomes a sentinel; on a hit, converts it in
     * place. Never cancels or blocks the spawn -- this is a modifier on an already-happening spawn,
     * not a summon. Standby, don't crash: nothing resolvable means the spawn proceeds unmodified.
     */
    public void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        if (!canBeSentinel(mob.getType())) {
            return;
        }

        Satchel.requireServer();
        // A lifted spawn already rolled at the placement gate; consuming here also retires the entry.
        boolean lifted = consumeLifted(mob.blockPosition());
        BossBundle bundle = getBundle();
        bundle.boss().ifPresent(bossFixture ->
                BorderAPI.CURVE(level).ifPresent(curves ->
                        tryMakeSentinel(mob, level, bossFixture, curves, lifted)));
    }

    private void tryMakeSentinel(Mob mob, ServerLevel level, BossFixture bossFixture,
                                 BorderCurveFixture curves, boolean lifted) {
        Optional<Nearby> nearbyOpt = resolveNearby(level, bossFixture, curves, mob.blockPosition());
        if (nearbyOpt.isEmpty()) {
            return;
        }
        Nearby nearby = nearbyOpt.get();

        // The two gates before the roll. A lifted spawn skips the roll, not the gates.
        int room = conversionRoom(bossFixture, nearby.boss());
        if (room < 1) {
            return;
        }
        if (!lifted && !rollPlacement(bossFixture, nearby)) {
            return;
        }

        // Difficulty reads the full border radius, not the placement fraction: toughness ramps
        // across the whole border.
        double difficultyIntensity = BorderMath.intensityAt(
                nearby.difficulty(), nearby.radius() > 0.0 ? nearby.distanceToBoss() / nearby.radius() : 1.0);
        int keeperPathPosition = BossAPI.pathPositionOf(nearby.boss());
        double target = bossFixture.RULES.sentinelTargetScale(keeperPathPosition);
        float size = fitScale(level, mob, target, bossFixture);

        applyConversion(mob, bossFixture, keeperPathPosition, difficultyIntensity);
        SentinelData.writeIfAbsent(mob, nearby.boss().bossId(), size);
        if (size != 1.0F) {
            // The mob was built at full size before it was known to be a sentinel; the record is what
            // the Size handler reads, so refresh now and it enters the world scaled.
            mob.refreshDimensions();
        }

        queueAdds(mob, nearby.boss().bossId(), size, target, keeperPathPosition, difficultyIntensity, bossFixture,
                room - 1);
    }

    /**
     * How many more Sentinels the level may take for a spawn near {@code keeper}: 0 when the Keeper's
     * path position is below {@code sentinelFirstPathPosition} or the level is at
     * {@code sentinelMaxPerLevel}, so a result of 1 or more passes both gates.
     */
    private int conversionRoom(BossFixture bossFixture, BossRecord keeper) {
        if (BossAPI.pathPositionOf(keeper) < bossFixture.RULES.sentinelFirstPathPosition()) {
            return 0;
        }
        return Math.max(0, bossFixture.RULES.sentinelMaxPerLevel() - sentinelCount());
    }

    /**
     * The Sentinels this level holds now, every Keeper's: those in presence, the ones that joined and
     * wait to be adopted, and the extra sentinels queued but not yet spawned. A Sentinel in an unloaded
     * chunk is not in presence, takes no room in the hostile spawn pool, and is not counted.
     */
    private int sentinelCount() {
        return present.count() + present.queued() + pendingAdds.size();
    }

    /**
     * The largest scale at most {@code min(target, sentinelMaxScale)} whose scaled hitbox fits at the
     * mob's position, stepping down from that ceiling by {@code sentinelFitStep} toward 1.0. Scale 1.0
     * needs no test: a natural spawn's spot has already passed vanilla's fit check for the unscaled mob.
     */
    private static float fitScale(ServerLevel level, Mob mob, double target, BossFixture boss) {
        double ceiling = Math.min(target, boss.RULES.sentinelMaxScale());
        double step = boss.RULES.sentinelFitStep();
        BlockPos pos = mob.blockPosition();
        for (double s = ceiling; s > 1.0 + 1.0E-6; s -= step) {
            if (MobSpawns.fits(level, mob, (float) s, pos)) {
                return (float) s;
            }
        }
        return 1.0F;
    }

    /**
     * The leftover budget {@code L = (target - 1) - (size - 1)} buys extra sentinels at
     * {@code sentinelAddCost} each: an expected count of {@code L / cost}, rolled whole-plus-
     * fractional and clamped to {@code sentinelMaxAddsPerSpawn} and to {@code roomForAdds}, the room
     * left under {@code sentinelMaxPerLevel} after this conversion. Queued, not spawned: never inside
     * the finalize-spawn dispatch that produced them.
     */
    private void queueAdds(Mob parent, UUID bossId, float size, double target, int keeperPathPosition,
                           double difficultyIntensity, BossFixture boss, int roomForAdds) {
        double leftover = (target - 1.0) - (size - 1.0);
        double cost = boss.RULES.sentinelAddCost();
        if (leftover <= 0.0 || cost <= 0.0 || roomForAdds <= 0) {
            return;
        }
        int cap = Math.min(boss.RULES.sentinelMaxAddsPerSpawn(), roomForAdds);
        int count = MobSpawns.rollCount(leftover / cost, cap, RNG);
        for (int i = 0; i < count; i++) {
            pendingAdds.add(new PendingAdd(parent.getType(), parent.getUUID(), bossId, size,
                    keeperPathPosition, difficultyIntensity));
        }
    }

    /**
     * The placement roll for a spawn that hasn't happened yet: true if {@code pos} is inside the
     * nearest boss's placement radius, the two conversion gates pass, and the roll hits. Standby, don't
     * crash: nothing resolvable means no.
     */
    public boolean rollPlacementAt(ServerLevel level, BlockPos pos) {
        BossBundle bundle = getBundle();
        Optional<BossFixture> bossOpt = bundle.boss();
        Optional<BorderCurveFixture> curvesOpt = BorderAPI.CURVE(level);
        if (bossOpt.isEmpty() || curvesOpt.isEmpty()) {
            return false;
        }
        BossFixture bossFixture = bossOpt.get();
        return resolveNearby(level, bossFixture, curvesOpt.get(), pos)
                .filter(nearby -> conversionRoom(bossFixture, nearby.boss()) >= 1)
                .map(nearby -> rollPlacement(bossFixture, nearby))
                .orElse(false);
    }

    /**
     * The nearest alive, positioned boss with a border to {@code pos}, with its border's radius and
     * its two sentinel curves. Distance is to the boss's own position, not the border's center:
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
            // A curve dropped on load (an unreadable shape) would otherwise stop sentinels for this
            // border until the next boss/border event; re-run the idempotent ensure and retry once.
            ensureSentinelCurves(level, borderId);
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
     * sentinelPlacementRadiusFraction)}, so the ramp is concentrated in a ring around the boss;
     * beyond that fraction the LINEAR curve reads 0 and nothing converts.
     */
    private boolean rollPlacement(BossFixture bossFixture, Nearby nearby) {
        double placementRadius = nearby.radius() * bossFixture.RULES.sentinelPlacementRadiusFraction();
        double normalized = placementRadius > 0.0 ? nearby.distanceToBoss() / placementRadius : 1.0;
        double intensity = BorderMath.intensityAt(nearby.placement(), normalized);
        if (intensity <= 0.0) {
            return false;
        }
        return RNG.nextDouble() < intensity * bossFixture.RULES.sentinelPlacementCoefficient();
    }
}
