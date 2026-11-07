package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.Dismissal;
import com.arryn.frontiermode.core.Presence;
import com.arryn.frontiermode.core.WidePathing;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * Everything the Sentinel module keeps at runtime (sentinels.md § The Sentinel module, § Presence): the Sentinels
 * present in the level ({@link Presence}), the extra Sentinels queued at conversion, the spawn positions where the
 * darkness gate was lifted, the alert windows and the defeat kill queue. It persists nothing: a Sentinel is a record
 * on the mob itself ({@link SentinelData}), so no record outside the mob can go stale. A Trinket on
 * {@code SENTINEL_JIG}, one per level scope on each side; it acts on the server only, and its client twin is
 * {@link SentinelClient}. Its tick is in two steps by phase: the writes into the synced fixture (the trail tuning,
 * adoption, drop) run before the fixtures tick, so the fixture's own tick pushes them in the tick they are made, and
 * the rest of the module logic runs after.
 *
 * <p>Conversion runs at spawn finalization, before the mob is in the world: it writes the record onto the mob. The
 * mob's join queues it in presence, and this Trinket's tick adopts it. The bodies were {@code BossSentinelsFixture}'s
 * and moved here with node G (FRO_292); the decision itself is {@link SentinelConversion}'s.
 */
final class SentinelPresence implements Ticking {

    // Transient: the spawn positions where the darkness gate was lifted (see the daylight rule),
    // cleared on the tick.
    private final Set<Long> liftedPositions = new HashSet<>();

    /** The Sentinels in the level now, and the joins waiting to be adopted. */
    private final Presence present = new Presence();

    // Whether the one-time sweep for Sentinels that joined before this Trinket existed has run.
    private boolean swept;

    private final Presence.Hooks presenceHooks = new Presence.Hooks() {
        @Override
        public boolean adopt(Mob mob, boolean fresh) {
            return adoptSentinel(mob, fresh);
        }

        @Override
        public void onDrop(Mob mob) {
            SentinelMarkers.fixture(mob.level()).ifPresent(fixture -> fixture.remove(mob.getUUID()));
        }
    };

    /** An extra sentinel queued at conversion, spawned on this Trinket's next tick. */
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

    /**
     * This level's presence; empty while Satchel or the level's scope isn't ready ("standby, don't crash").
     * Server thread.
     */
    static Optional<SentinelPresence> of(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        return Satchel.trinket(FrontierKeys.SENTINEL_JIG, new LevelScope(level), SentinelPresence.class);
    }

    // ── presence ─────────────────────────────────────────────────────────────

    /**
     * A mob that carries the Sentinel record has joined the level, fresh or loaded from disk. It is
     * queued, and this Trinket's tick adopts it.
     */
    void join(Mob mob, boolean fresh) {
        present.join(mob, fresh);
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
        Optional<BossFixture> bossOpt = BossAPI.bosses(mob.level());
        if (facts.isEmpty() || bossOpt.isEmpty()) {
            return false;
        }
        boolean bossStands = bossOpt.get().CRUD.get(facts.get().bossId()).map(BossRecord::alive).orElse(false);
        if (!bossStands) {
            Dismissal.dismiss(mob);
            return false;
        }
        SentinelMarkers.fixture(mob.level()).ifPresent(fixture -> fixture.add(mob.getUUID(), facts.get().scale()));
        mob.refreshDimensions();
        if (fresh) {
            SentinelRules.healSentinel(mob);
        }
        return true;
    }

    // ── defeat ───────────────────────────────────────────────────────────────

    /**
     * The boss {@code bossId} was defeated: queues every present sentinel that names it for a staggered
     * kill on this Trinket's tick. {@code creditedPlayer} is the player who defeated it, so experience
     * and player-only drops follow; {@code null} (a forced defeat) kills uncredited. Only this boss's
     * own sentinels, whether or not its siblings still stand. One that joins later is dismissed at
     * adoption.
     */
    void doomSentinelsOf(UUID bossId, UUID creditedPlayer) {
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
    void alertSentinelsOf(ServerLevel level, UUID bossId, Mob boss, ServerPlayer player) {
        double radius = SentinelRules.sentinelAlertRadius();
        long alertedUntil = level.getGameTime() + SentinelRules.sentinelAlertMemoryTicks();
        AABB box = AABB.unitCubeFromLowerCorner(boss.position()).inflate(radius, ALERT_HEIGHT, radius);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (!present.contains(mob.getUUID()) || !isSentinelOf(mob, bossId)) {
                continue;
            }
            if (SentinelRules.alertSentinel(mob, player)) {
                alerts.put(mob.getUUID(), new Alert(player.getUUID(), alertedUntil));
            }
        }
    }

    /**
     * Retires lapsed alerts. A sentinel whose window has run out is let go through
     * {@code SentinelRules#releaseSentinel}, which keeps it on the player only if it can see them. An
     * entry whose sentinel is gone, unloaded or dead, or whose player has logged out, is simply
     * dropped.
     */
    private void releaseLapsedAlerts(ServerLevel level) {
        if (alerts.isEmpty()) {
            return;
        }
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
                SentinelRules.releaseSentinel(sentinel, player);
            }
        }
    }

    // ── lifted spawn positions ───────────────────────────────────────────────

    /** Records a position where the darkness gate was lifted for a spawn that will become a sentinel. */
    void markLifted(BlockPos pos) {
        liftedPositions.add(pos.asLong());
    }

    /** True if {@code pos} is a recorded lifted position. Read only: finalize-spawn consumes the entry. */
    boolean isLifted(BlockPos pos) {
        return liftedPositions.contains(pos.asLong());
    }

    private boolean consumeLifted(BlockPos pos) {
        return liftedPositions.remove(pos.asLong());
    }

    // ── the tick ─────────────────────────────────────────────────────────────

    /**
     * Server only, before the fixtures tick: the phase for writing into them (trinket-tick.md § The tick, in three
     * steps), so {@code SentinelFixture}'s own tick pushes each change in the tick it is made. Waits for the boss
     * records (a Sentinel is adopted against them), then copies the trail's tuning into the synced fixture, adopts the
     * Sentinels that joined and drops the ones that left. Presence goes first so a parent that joined this tick is
     * present when {@link #onTickAfter} spawns its adds.
     */
    @Override
    public void onTickBefore(ScopeInfo info) {
        Optional<ServerLevel> ready = readyServerLevel(info);
        if (ready.isEmpty()) {
            return;
        }
        ServerLevel level = ready.get();

        SentinelMarkers.fixture(level).ifPresent(fixture -> fixture.setTrail(
                SentinelRules.trailIntervalTicks(), SentinelRules.trailLifetimeTicks(), SentinelRules.trailRadius()));

        if (!swept) {
            swept = true;
            joinSentinelsAlreadyInLevel(level);
        }
        present.tick(level, presenceHooks);
    }

    /**
     * Server only, after the internal walk: the module logic that writes nothing into the fixture. Spawns the queued
     * extra sentinels, runs the defeat kills, releases lapsed alerts, nudges the wide Sentinels along their paths, and
     * clears the lifted spawn positions.
     */
    @Override
    public void onTickAfter(ScopeInfo info) {
        Optional<ServerLevel> ready = readyServerLevel(info);
        if (ready.isEmpty()) {
            return;
        }
        ServerLevel level = ready.get();

        spawnPendingAdds(level);
        killDoomed(level);
        releaseLapsedAlerts(level);
        unstickWideSentinels();
        liftedPositions.clear();
    }

    /**
     * The level this tick runs on: empty on the client, or while the boss records aren't known yet (there is nothing
     * to adopt, kill or alert against).
     */
    private static Optional<ServerLevel> readyServerLevel(ScopeInfo info) {
        if (!Satchel.isServer()) {
            return Optional.empty();
        }
        LevelScope scope = info.scopeAs();
        ServerLevel level = (ServerLevel) scope.level();
        return BossAPI.bosses(level).isPresent() ? Optional.of(level) : Optional.empty();
    }

    /**
     * Once, on the first tick: queues the Sentinels already in the level. A Sentinel in a chunk loaded before this
     * level's scope was ready joined while there was nothing to take it, so its join never reached {@link #join}.
     */
    private void joinSentinelsAlreadyInLevel(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && SentinelData.has(mob)) {
                present.join(mob, false);
            }
        }
    }

    /** FRO_224: advances a wide Sentinel's path when it stands on its next waypoint; see {@link WidePathing}. */
    private void unstickWideSentinels() {
        for (Mob mob : present.mobs()) {
            WidePathing.advanceIfOnWaypoint(mob);
        }
    }

    /**
     * Kills up to {@code sentinelDefeatKillsPerTick} of the queued sentinels that are present. One that
     * is no longer present (unloaded) is skipped and costs nothing: it is dismissed when it next loads,
     * because its boss is defeated.
     */
    private void killDoomed(ServerLevel level) {
        if (pendingKills.isEmpty()) {
            return;
        }
        int budget = SentinelRules.sentinelDefeatKillsPerTick();
        while (budget > 0 && !pendingKills.isEmpty()) {
            PendingKill kill = pendingKills.poll();
            Optional<Entity> entity = Satchel.require().egress().getEntity(level, kill.mobUuid);
            if (entity.isEmpty() || !(entity.get() instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            // A credited player who has left the level by now: the sentinel dies uncredited.
            ServerPlayer credited = kill.creditedPlayer != null
                    && level.getPlayerByUUID(kill.creditedPlayer) instanceof ServerPlayer player ? player : null;
            SentinelRules.killSentinel(mob, credited);
            budget--;
        }
    }

    private void spawnPendingAdds(ServerLevel level) {
        if (pendingAdds.isEmpty()) {
            return;
        }
        List<PendingAdd> adds = List.copyOf(pendingAdds);
        pendingAdds.clear();

        for (PendingAdd add : adds) {
            // Only if the parent actually joined the level: a parent that never made it in brings
            // no adds. Presence has checked that the parent is the same object the level holds.
            Optional<Mob> parent = present.get(add.parentUuid);
            if (parent.isEmpty()) {
                continue;
            }
            spawnAdd(level, parent.get(), add);
        }
    }

    /**
     * One extra sentinel: the parent's own type and size, scattered near the parent, with
     * {@code VANILLA} setup so it never re-enters the spawn fan-out (never rolled for conversion,
     * never spawns adds of its own). Conversion's treatment is applied in {@code preSetup}; its
     * join queues it in presence like any sentinel's.
     */
    private void spawnAdd(ServerLevel level, Mob parent, PendingAdd add) {
        BlockPos spot = MobSpawns.scatter(level, parent, parent.blockPosition(),
                SentinelRules.sentinelAddScatterRadius(), add.scale, ADD_SCATTER_TRIES);
        MobSpawns.spawn(level, add.type, spot, MobSpawns.Setup.VANILLA, mob -> {
            SentinelData.writeIfAbsent(mob, add.bossId, add.scale);
            SentinelConversion.applyConversion(mob, add.keeperPathPosition, add.difficultyIntensity);
            mob.refreshDimensions();
        });
    }

    // ── conversion ───────────────────────────────────────────────────────────

    /**
     * Called by {@code SentinelModule.onMobSpawnFinalize} for every mob about to finalize-spawn on this
     * Trinket's own level. Rolls whether this spawn becomes a sentinel; on a hit, converts it in
     * place. Never cancels or blocks the spawn -- this is a modifier on an already-happening spawn,
     * not a summon. Standby, don't crash: nothing resolvable means the spawn proceeds unmodified.
     */
    void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        if (!SentinelConversion.canBeSentinel(mob.getType())) {
            return;
        }

        Satchel.requireServer();
        // A lifted spawn already rolled at the placement gate; consuming here also retires the entry.
        boolean lifted = consumeLifted(mob.blockPosition());
        BossAPI.bosses(level).ifPresent(bossFixture ->
                BorderAPI.CURVE(level).ifPresent(curves ->
                        tryMakeSentinel(mob, level, bossFixture, curves, lifted)));
    }

    private void tryMakeSentinel(Mob mob, ServerLevel level, BossFixture bossFixture,
                                 BorderCurveFixture curves, boolean lifted) {
        Optional<SentinelConversion.Nearby> nearbyOpt =
                SentinelConversion.resolveNearby(level, bossFixture, curves, mob.blockPosition());
        if (nearbyOpt.isEmpty()) {
            return;
        }
        SentinelConversion.Nearby nearby = nearbyOpt.get();

        // The two gates before the roll. A lifted spawn skips the roll, not the gates.
        int room = SentinelConversion.conversionRoom(nearby.keeper(), sentinelCount());
        if (room < 1) {
            return;
        }
        if (!lifted && !SentinelConversion.rollPlacement(nearby)) {
            return;
        }

        double difficultyIntensity = SentinelConversion.difficultyIntensity(nearby);
        int keeperPathPosition = KeeperAPI.pathPositionOf(nearby.keeper());
        double target = SentinelRules.sentinelTargetScale(keeperPathPosition);
        float size = SentinelConversion.fitScale(level, mob, target);

        SentinelConversion.applyConversion(mob, keeperPathPosition, difficultyIntensity);
        SentinelData.writeIfAbsent(mob, nearby.boss().bossId(), size);
        if (size != 1.0F) {
            // The mob was built at full size before it was known to be a sentinel; the record is what
            // the Size handler reads, so refresh now and it enters the world scaled.
            mob.refreshDimensions();
        }

        queueAdds(mob, nearby.boss().bossId(), size, target, keeperPathPosition, difficultyIntensity, room - 1);
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
     * Queues the extra sentinels the leftover budget buys ({@link SentinelConversion#addCount}). Queued, not
     * spawned: never inside the finalize-spawn dispatch that produced them.
     */
    private void queueAdds(Mob parent, UUID bossId, float size, double target, int keeperPathPosition,
                           double difficultyIntensity, int roomForAdds) {
        int count = SentinelConversion.addCount(target, size, roomForAdds);
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
    boolean rollPlacementAt(ServerLevel level, BlockPos pos) {
        Optional<BossFixture> bossOpt = BossAPI.bosses(level);
        Optional<BorderCurveFixture> curvesOpt = BorderAPI.CURVE(level);
        if (bossOpt.isEmpty() || curvesOpt.isEmpty()) {
            return false;
        }
        return SentinelConversion.resolveNearby(level, bossOpt.get(), curvesOpt.get(), pos)
                .filter(nearby -> SentinelConversion.conversionRoom(nearby.keeper(), sentinelCount()) >= 1)
                .map(SentinelConversion::rollPlacement)
                .orElse(false);
    }
}
