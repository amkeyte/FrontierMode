package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.BossKind;
import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.frontiermode.boss.common.SiteOutcome;
import com.arryn.frontiermode.boss.common.Spawned;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.MaterializeOutcome;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickDelay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The level-scope Trinket on {@code BOSS_JIG} that holds a level's {@link Boss}es, on each side (wiki/
 * frontiermode/architecture/boss.md § The Boss object and the roster). One per level, built by Satchel when
 * the scope loads; it keeps no state of its own that is not derived from the fixture and the mobs, apart from
 * the delayed flush.
 *
 * <p><b>It derives from state every tick,</b> on both sides: it builds a {@code Boss} for a whole entry, drops
 * one for a removed record, and pushes or ends interest on its side's {@code BOSS_MOB_JIG} as a record's
 * {@code bossEntityId} is set or cleared. On the server the changes come from the validated mutations; on the
 * client they arrive in a parcel. It waits on {@link #isReady} until the first parcel has arrived.
 *
 * <p><b>It builds a {@code Boss} only from a whole entry:</b> the base record and the kind's own record (the
 * kind answers {@code isComplete}). The two live in different bundles, so on a client they arrive as separate
 * parcels, in either order; a half entry waits, and the server follows the same rule so that both sides run
 * one code path. An entry still half after {@link #ENTRY_TIMEOUT_TICKS} is a programming error and throws.
 *
 * <p>It hands each {@code Boss} it builds to the {@link RecordAbilities} Trinket before any lifecycle step, and
 * takes it back when it drops the {@code Boss}. On the server it also drives each {@code Boss}'s base lifecycle
 * and owns the delayed durability flush (boss.md § Cascade durability).
 */
public final class BossRoster implements Ticking {

    /**
     * Ticks a half entry may wait before it is a programming error. A constant of the roster, not a
     * {@code Tunable}: {@code Tunable}s are server-only and the client needs the same number.
     */
    static final int ENTRY_TIMEOUT_TICKS = 600;

    private static final long FLUSH_DELAY_TICKS = 20;

    // By bossId, in the order the roster built them.
    private final Map<UUID, Boss> bosses = new LinkedHashMap<>();

    // The entity-to-Boss map: the entity ids this roster holds interest in.
    private final Map<UUID, Boss> byEntity = new HashMap<>();

    // bossId -> the entity id this roster holds interest in on its side's BOSS_MOB_JIG.
    private final Map<UUID, UUID> held = new HashMap<>();

    // bossId -> an entity id whose interest has ended on a terminal leave while the record still names it
    // (a defeated record's dead body), so it is not pushed again.
    private final Map<UUID, UUID> ended = new HashMap<>();

    // bossId -> ticks a half entry has waited.
    private final Map<UUID, Integer> waiting = new HashMap<>();

    // The server's delayed durability flush; null means nothing pending.
    private TickDelay pendingFlush;

    // The level's base fixture, as of the last tick; what the record lookups read.
    private BossFixture fixture;

    // The record-abilities Trinket, declared after this one; found on the first tick, when it is attached.
    private RecordAbilities recordAbilities;

    /** Ready once the base fixture is: on a client, once the first parcel has arrived. */
    @Override
    public boolean isReady(ScopeInfo info) {
        return info.ask(FrontierKeys.BOSS_BUNDLE)
                .flatMap(BossBundle::boss)
                .map(SatchelFixture::isReady)
                .orElse(false);
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        LevelScope scope = info.scopeAs();
        Level level = scope.level();

        BossBundle bundle = info.getOrCreate(FrontierKeys.BOSS_BUNDLE);
        fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS, BossFixture::new);

        if (recordAbilities == null) {
            // Empty means the scope is not standing yet: standby, and no Boss is built without its abilities.
            recordAbilities = Satchel.trinket(FrontierKeys.BOSS_JIG, scope, RecordAbilities.class).orElse(null);
            if (recordAbilities == null) {
                return;
            }
        }

        reconcile(level, fixture);

        if (Satchel.isServer()) {
            pollFlush(bundle);
            for (Boss boss : List.copyOf(bosses.values())) {
                boss.advance(level, fixture);
            }
        }
    }

    /** The scope is unloading: end every interest this roster pushed, since Satchel does not clear it. */
    @Override
    public void onDetach(ScopeInfo info) {
        if (!held.isEmpty()) {
            for (UUID entityId : List.copyOf(held.values())) {
                Satchel.mobJig(FrontierKeys.BOSS_MOB_JIG).lostInterest(entityId);
            }
        }
        fixture = null;
        recordAbilities = null;
        held.clear();
        ended.clear();
        byEntity.clear();
        bosses.clear();
        waiting.clear();
        pendingFlush = null;
    }

    @Override
    public String describe() {
        return bosses.size() + " boss(es), " + held.size() + " held interest(s)";
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** The {@code Boss} for a boss id, if its entry is whole. */
    public Optional<Boss> boss(UUID bossId) {
        return Optional.ofNullable(bosses.get(bossId));
    }

    /** The {@code Boss} whose live body has this entity id: "find the boss for this entity". */
    public Optional<Boss> bossFor(UUID entityId) {
        return Optional.ofNullable(byEntity.get(entityId));
    }

    /** A snapshot of every {@code Boss} this level holds. */
    public List<Boss> bosses() {
        return List.copyOf(bosses.values());
    }

    /** How many bosses of {@code kindKey} this level holds. */
    public long count(String kindKey) {
        return bosses.values().stream().filter(boss -> boss.kind().equals(kindKey)).count();
    }

    /**
     * Whether the entity with this id is a live boss: one this roster holds whose record is alive. The lookup
     * {@code BossMarkers} answers from, on the server and, through its cache, on the client.
     */
    public boolean isLive(UUID entityId) {
        return recordOf(entityId).map(BossRecord::alive).orElse(false);
    }

    /** The size multiplier of the boss with this entity id, from its record, or {@code 1.0F} for anything else. */
    public float scaleOf(UUID entityId) {
        return recordOf(entityId).map(BossRecord::scale).orElse(1.0F);
    }

    private Optional<BossRecord> recordOf(UUID entityId) {
        Boss boss = byEntity.get(entityId);
        if (boss == null || fixture == null) {
            return Optional.empty();
        }
        return fixture.get(boss.bossId());
    }

    // ------------------------------------------------------------------
    // Reconciliation, on both sides
    // ------------------------------------------------------------------

    private void reconcile(Level level, BossFixture fixture) {
        Set<UUID> recorded = new HashSet<>();
        for (BossRecord record : fixture.all()) {
            UUID bossId = record.bossId();
            recorded.add(bossId);

            Boss boss = bosses.get(bossId);
            if (boss == null) {
                BossKind kind = BossKinds.get(record.kind()).orElseThrow(() -> new IllegalStateException(
                        "[Boss] reconcile(): bossId=" + bossId + " names kind \"" + record.kind()
                                + "\", which is not registered."));
                if (!kind.isComplete(level, bossId)) {
                    int waited = waiting.merge(bossId, 1, Integer::sum);
                    if (waited > ENTRY_TIMEOUT_TICKS) {
                        throw new IllegalStateException("[Boss] reconcile(): the entry for bossId=" + bossId
                                + " is still half after " + ENTRY_TIMEOUT_TICKS + " ticks: kind \"" + record.kind()
                                + "\" has no record of its own for it.");
                    }
                    continue;
                }
                waiting.remove(bossId);
                boss = new Boss(bossId, record.kind(), this);
                bosses.put(bossId, boss);
                // Before syncInterest and any advance: a body that materializes on this tick already has its
                // Persistent, and a loss in the meantime is not read as a transient boss's.
                recordAbilities.bossBuilt(level, boss);
            }
            syncInterest(boss, record);
        }

        for (Iterator<Map.Entry<UUID, Boss>> it = bosses.entrySet().iterator(); it.hasNext(); ) {
            UUID bossId = it.next().getKey();
            if (recorded.contains(bossId)) {
                continue;
            }
            UUID entityId = held.get(bossId);
            if (entityId != null) {
                stopHolding(bossId, entityId);
            }
            ended.remove(bossId);
            recordAbilities.bossDropped(bossId);
            it.remove();
        }
        waiting.keySet().retainAll(recorded);
        // A transient boss is dropped at its death, ahead of the leave that follows it by about 20 ticks, and that
        // leave ends interest for a boss this roster no longer holds.
        ended.keySet().retainAll(recorded);
    }

    /**
     * Pushes interest when the record names an entity (alive or not, so a defeated record whose body is
     * unloaded keeps its interest), and ends it when the record stops naming it.
     */
    private void syncInterest(Boss boss, BossRecord record) {
        UUID bossId = boss.bossId();
        UUID wanted = record.bossEntityId();
        UUID current = held.get(bossId);

        if (wanted == null || !wanted.equals(ended.get(bossId))) {
            ended.remove(bossId);
        }
        if (wanted != null && wanted.equals(current)) {
            return;
        }
        if (current != null) {
            stopHolding(bossId, current);
        }
        if (wanted != null && !wanted.equals(ended.get(bossId))) {
            startHolding(boss, wanted);
        }
    }

    /** The entity-to-{@code Boss} map is updated before the interest call, as the spawn order requires. */
    private void startHolding(Boss boss, UUID entityId) {
        byEntity.put(entityId, boss);
        held.put(boss.bossId(), entityId);
        Satchel.mobJig(FrontierKeys.BOSS_MOB_JIG).interest(entityId);
    }

    private void stopHolding(UUID bossId, UUID entityId) {
        held.remove(bossId);
        byEntity.remove(entityId);
        Satchel.mobJig(FrontierKeys.BOSS_MOB_JIG).lostInterest(entityId);
    }

    /**
     * A terminal {@code Leaving}, or a defeated body dismissed on load, ends the interest in {@code entityId}.
     * The record may still name it, so it is remembered and not pushed again.
     */
    void endInterest(UUID bossId, UUID entityId) {
        if (entityId.equals(held.get(bossId))) {
            stopHolding(bossId, entityId);
        }
        ended.put(bossId, entityId);
    }

    // ------------------------------------------------------------------
    // Materialization, on the server
    // ------------------------------------------------------------------

    /**
     * The mob a kind spawned is in the level: the order is fixed (boss.md § Spawn). The record's materialize
     * sets the entity id and the scale, a {@code Persistent} boss makes the body persistent, then the
     * entity-to-{@code Boss} map is updated, then {@code interest} returns the mob scope and its Trinkets attach.
     */
    void materialized(Boss boss, BossFixture fixture, Spawned spawned) {
        Mob mob = spawned.mob();
        UUID bossId = boss.bossId();

        fixture.CRUD.materialize(bossId, mob.getUUID(), spawned.scale());
        boss.onMaterialized(mob);

        UUID old = held.get(bossId);
        if (old != null) {
            stopHolding(bossId, old);
        }
        ended.remove(bossId);
        startHolding(boss, mob.getUUID());
    }

    /**
     * Forces materialization of {@code bossId} regardless of chunk-loaded state ({@code /boss mob spawn}):
     * the same {@code Boss} path as the tick, except it loads the chunk synchronously (safe: only ever called
     * from a player command), works on a record that has no entity or a stale one, and clears the respawn
     * state, which is the way back from a repeat-death suspension. An entry that is not whole answers
     * {@code NO_RECORD}.
     */
    MaterializeOutcome forceMaterialize(Level level, BossFixture fixture, UUID bossId) {
        Boss boss = bosses.get(bossId);
        Optional<BossRecord> recordOpt = fixture.get(bossId);
        if (recordOpt.isEmpty() || boss == null) {
            OUT.warn("[Boss] forceMaterialize(): no record, or an entry that is not whole, for bossId="
                    + Ids.shortId(bossId));
            return MaterializeOutcome.NO_RECORD;
        }
        BossRecord record = recordOpt.get();

        if (!record.positioned()) {
            OUT.warn("[Boss] forceMaterialize(): bossId=" + Ids.shortId(bossId)
                    + " has no finalized position yet (home border still pregenerating)"
                    + " -- cannot force-materialize.");
            return MaterializeOutcome.NOT_POSITIONED;
        }

        BlockPos xz = record.position();
        boolean replacing = false;
        if (record.materialized()) {
            // The record says it has an entity. If that entity is alive out in the world, an admin who
            // asked for a spawn wants it home: teleport it, as the leash would.
            Optional<Entity> existing = Satchel.require().egress().getEntity(level, record.bossEntityId());
            if (existing.isPresent() && existing.get() instanceof Mob live && live.isAlive()) {
                live.teleportTo(xz.getX() + 0.5, xz.getY(), xz.getZ() + 0.5);
                live.setTarget(null);
                live.getNavigation().stop();
                OUT.info("[Boss] forceMaterialize(): bossId=" + Ids.shortId(bossId) + " was alive at "
                        + live.blockPosition() + " -- teleported it back to " + xz);
                return MaterializeOutcome.TELEPORTED_HOME;
            }
            // No live entity: it was removed without a death event, converted, or is in an unloaded
            // chunk. An admin override replaces it; if the old one is only unloaded it may reappear.
            OUT.warn("[Boss] forceMaterialize(): bossId=" + Ids.shortId(bossId) + " is recorded as"
                    + " materialized (entity " + Ids.shortId(record.bossEntityId()) + ") but none is present"
                    + " -- clearing the stale entity and spawning a fresh one. If the old entity was only"
                    + " unloaded it may reappear later.");
            fixture.CRUD.dematerialize(bossId);
            replacing = true;
        }

        OUT.info("[Boss] forceMaterialize(): forcing chunk load at " + xz
                + " for bossId=" + Ids.shortId(bossId));
        level.getChunk(xz);

        BossKind kind = BossKinds.get(boss.kind()).orElseThrow();
        if (kind.prepareSite(level, bossId, xz) == SiteOutcome.WAIT) {
            OUT.warn("[Boss] forceMaterialize(): kind " + boss.kind() + " is not ready to prepare the site for"
                    + " bossId=" + Ids.shortId(bossId) + " at " + xz + ".");
            return MaterializeOutcome.DECLINED;
        }
        Optional<Spawned> spawned = kind.spawn(level, bossId, xz);
        if (spawned.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): kind " + boss.kind() + " declined to spawn bossId="
                    + Ids.shortId(bossId) + " at " + xz + " (e.g. an all-liquid column).");
            return MaterializeOutcome.DECLINED;
        }

        materialized(boss, fixture, spawned.get());
        // FRO_160: an admin spawn is the way back from a repeat-death suspension.
        boss.clearRespawnState();
        OUT.info("[Boss] forceMaterialize(): spawned bossId=" + Ids.shortId(bossId)
                + " entity=" + Ids.shortId(spawned.get().mob().getUUID()) + " at "
                + spawned.get().mob().blockPosition());

        return replacing ? MaterializeOutcome.SPAWNED_REPLACING : MaterializeOutcome.SPAWNED;
    }

    // ------------------------------------------------------------------
    // The delayed durability flush (boss.md § Cascade durability), on the server
    // ------------------------------------------------------------------

    /**
     * Schedules a single delayed {@code forceWriteToDisk()} call, or coalesces into one already pending on
     * this level. Called after every {@code markDefeated} that returns {@code true} and after every
     * {@code dematerialize} -- regardless of whether the kind's {@code onDefeat} then does anything, since
     * the mark itself is a real mutation worth protecting. By the time the delay elapses, every bundle the
     * cascade mutated has had its own automatic {@code EXECUTION_PULSE} refresh run, so the one forced write
     * catches every sibling through the level-wide {@code save()}.
     */
    void scheduleFlush() {
        if (pendingFlush == null) {
            pendingFlush = new TickDelay(FLUSH_DELAY_TICKS, new SchedulerClock.AutoClock());
        } else {
            // An earlier, still-pending flush on this level -- coalesce two near-simultaneous defeats into
            // one eventual write instead of two.
            pendingFlush.reset();
        }
    }

    /**
     * Fires the pending delayed flush once its delay elapses, then clears it back to {@code null} rather
     * than leaving it in its fired state, so a later defeat starts a genuinely fresh delay.
     */
    private void pollFlush(BossBundle bundle) {
        if (pendingFlush != null && pendingFlush.allow()) {
            bundle.forceWriteToDisk();
            pendingFlush = null;
        }
    }
}
