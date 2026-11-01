package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.boss.common.BossCreation;
import com.arryn.frontiermode.boss.common.BossKind;
import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Level-scoped ({@code LevelScope}), persisted and networked collection of base {@link BossRecord}s -- the
 * sole durable source of truth for boss identity, per wiki/frontiermode/architecture/boss.md's "Data
 * model" section. Lives in its own {@link com.arryn.frontiermode.boss.common.bundle.BossBundle}. The client
 * receives the fixture as full-state parcels and runs the same code over it; every mutation is the server's
 * ({@code Satchel.requireServer()}), and the ones a client reads push promptly instead of waiting out
 * Satchel's sync interval.
 *
 * <p>A kind that keeps a record of its own (the Keeper's {@code KeeperRecord}) keeps it in its own module and
 * bundle under the same {@code bossId}; {@link #create} and {@link #remove} reach it through the kind's hooks.
 *
 * <p>Split into the {@link #CRUD}/{@link #RULES}/{@link #INFO} facets, mirroring {@code BordersFixture}'s
 * split. This fixture's own methods stay {@code public} rather than being narrowed to package-private --
 * {@code BossCommandHandler} and {@code BossSelector} hold direct {@link BossFixture} references from
 * {@code BossAPI.bosses(Level)}. The facets are thin pass-throughs onto these same methods.
 */
public final class BossFixture extends SatchelFixture {

    private static final String KEY_BOSSES = "bosses";

    /**
     * FRO_081: record lifecycle/mutations -- reach through {@code BossAPI.CRUD(Level)}
     * from outside {@code boss.common.fixture} where possible, mirroring
     * {@code BorderAPI.CRUD(Level)}. See this class's own doc for why {@code BossFixture}'s
     * methods themselves stay public rather than being narrowed the way {@code BordersFixture}'s
     * were.
     */
    public final BossCrudFacet CRUD = new BossCrudFacet(this);

    /**
     * FRO_081: pluggable position/materialization strategy -- reach through
     * {@code BossAPI.RULES(Level)} where possible, mirroring {@code BorderAPI.RULES(Level)}.
     */
    public final BossRulesFacet RULES = new BossRulesFacet(this);

    /**
     * FRO_081: queries/diagnostics -- reach through {@code BossAPI.INFO(Level)} where possible,
     * mirroring {@code BorderAPI.INFO(Level)}.
     */
    public final BossInfoFacet INFO = new BossInfoFacet(this);

    private final List<BossRecord> bosses = new ArrayList<>();

    public BossFixture() {
        registerCustom(
                KEY_BOSSES,
                this::saveBosses,
                this::loadBosses
        );
    }

    private void saveBosses(CompoundTag root) {
        Satchel.requireServer();

        ListTag list = new ListTag();
        for (BossRecord record : bosses) {
            list.add(BossRecord.save(record));
        }
        root.put(KEY_BOSSES, list);
    }

    private void loadBosses(CompoundTag root) {
        if (!root.contains(KEY_BOSSES, Tag.TAG_LIST)) {
            return;
        }
        bosses.clear();

        ListTag list = root.getList(KEY_BOSSES, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            bosses.add(BossRecord.load((CompoundTag) t));
        }
    }

    @Override
    public void onLoaded() {
        super.onLoaded();
        OUT.info("            Bosses loaded: " + bosses.size());
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * FRO_060 hotfix, found via real playtest (not by reading source alone): this used to return
     * {@code Collections.unmodifiableList(bosses)} -- a live *view* over the mutable backing
     * list, not a snapshot. {@code BossSelector.resolve()}'s {@code @all} case hands this
     * straight to {@code BossCommands.applySelector}'s {@code for (BossRecord b : bosses)} loop,
     * and {@code /boss transform defeat @all} is the one boss operation that adds a brand-new
     * record as a side effect mid-loop ({@code forceDefeat} -> {@code createBoss} ->
     * {@code create()} -> {@code bosses.add(...)}) -- structurally modifying the exact list the
     * for-each is iterating, which throws {@code ConcurrentModificationException} on the
     * iterator's next advance. That exception is thrown by the for-each's own iterator
     * bookkeeping, not by {@code op.run(...)}, so it lands outside
     * {@code applySelector}'s per-boss {@code try/catch} and aborts the whole command --
     * "defeats one boss, then errors out" is exactly what that looks like from the chat. A real
     * defensive copy here (not a live wrapper) closes it at the fixture boundary, the same
     * "fix it where the data crosses out of the fixture" shape FRO_058's validation boundary
     * used -- every current caller (selector resolution, {@code BossCommandHandler.info}'s
     * {@code indexOf}, {@code BossModule}'s one-shot lookups) already treats the result as a
     * point-in-time snapshot, so this changes nothing for them.
     */
    public List<BossRecord> all() {
        return List.copyOf(bosses);
    }

    public Optional<BossRecord> get(UUID bossId) {
        return bosses.stream()
                .filter(r -> r.bossId().equals(bossId))
                .findFirst();
    }

    /**
     * Records with {@code alive: true}, a finalized (non-null) {@code position}, and
     * {@code bossEntityId == null} -- positioned but not yet materialized. {@code BOSS_JIG}'s own
     * tick walks this every cycle (see {@code boss.md}'s "Three questions, three different
     * mechanisms"). Border Pregeneration added the {@link BossRecord#positioned()} requirement --
     * a record with no finalized position yet has nothing this method's caller could act on
     * (see {@link #unpositioned()}, the earlier stage a record passes through first).
     */
    public List<BossRecord> unmaterialized() {
        return bosses.stream()
                .filter(r -> r.alive() && r.positioned() && !r.materialized())
                .toList();
    }

    /**
     * Records with {@code alive: true} and no finalized {@code position} yet -- Border
     * Pregeneration's own new stage, ahead of materialization. {@code BOSS_JIG}'s own tick walks
     * this every cycle, no-opping on any record whose home border isn't
     * {@code BorderAPI.isPregenReady()} yet, per boss.md's "Three questions, three different
     * mechanisms" section.
     */
    public List<BossRecord> unpositioned() {
        return bosses.stream()
                .filter(r -> r.alive() && !r.positioned())
                .toList();
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    /**
     * Marks the fixture dirty and pushes it to clients now instead of waiting out Satchel's sync interval
     * (SAT_081), so a client sees the change when it lands. A push with nobody in the dimension is skipped
     * by Satchel itself.
     */
    private void changed() {
        markDirty();
        getBundle().forceSync();
    }

    private Level level() {
        return ((BossBundle) getBundle()).level();
    }

    /**
     * Creates a boss of kind {@code kindKey}: writes the base record, then calls the kind's
     * {@link BossKind#onCreate}, which writes the kind's own record from {@code args}, in one call (boss.md §
     * Creation is one operation). Position starts unset; see {@link #finalizePosition}.
     *
     * <p>Rejects an unknown kind, and a kind that refuses its arguments (the Keeper refuses {@code layer < 1}),
     * with a logged {@code OUT.warn} and an empty return; a rejected create leaves neither record behind.
     */
    public Optional<BossRecord> create(String kindKey, BossCreation args) {
        Satchel.requireServer();
        Objects.requireNonNull(args, "args");

        Optional<BossKind> kindOpt = BossKinds.get(kindKey);
        if (kindOpt.isEmpty()) {
            OUT.warn("[Boss] create(): rejected -- no boss kind is registered under \"" + kindKey + "\".");
            return Optional.empty();
        }

        BossRecord record = new BossRecord(UUID.randomUUID(), kindKey, null, null, true, 1.0F);
        bosses.add(record);
        if (!kindOpt.get().onCreate(level(), record.bossId(), args)) {
            bosses.remove(record);
            return Optional.empty();
        }

        changed();
        return Optional.of(record);
    }

    /**
     * Finalizes an unpositioned record's {@code position}, once the kind says it is final. Rejects a record
     * that already has a finalized position (defensive, mirroring {@link #materialize}'s identical guard) --
     * returns {@code false} rather than silently overwriting a committed position.
     */
    public boolean finalizePosition(UUID bossId, BlockPos resolvedPosition) {
        Satchel.requireServer();
        Objects.requireNonNull(resolvedPosition, "resolvedPosition");

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Boss] finalizePosition(): no record for bossId=" + Ids.shortId(bossId) + " -- ignoring.");
            return false;
        }

        BossRecord record = existing.get();
        if (record.positioned()) {
            OUT.warn("[Boss] finalizePosition(): bossId=" + Ids.shortId(bossId) + " already has a finalized"
                    + " position (" + record.position() + ") -- ignoring.");
            return false;
        }

        BossRecord updated = record.finalizedAt(resolvedPosition);
        bosses.removeIf(r -> r.bossId().equals(bossId));
        bosses.add(updated);
        changed();
        return true;
    }

    /**
     * Records materialization: the vanilla mob has been spawned at a resolved ground position, at the kind's
     * body size {@code scale}. Replaces the record in place (records are immutable) -- same remove-then-add-back
     * shape {@code BordersFixture.reassignLayers} uses.
     *
     * @return true only if the record existed, was unmaterialized, and is now materialized with that scale
     */
    public boolean materialize(UUID bossId, UUID entityId, float scale) {
        Satchel.requireServer();

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Boss] materialize(): no record for bossId=" + Ids.shortId(bossId) + " -- ignoring.");
            return false;
        }

        BossRecord record = existing.get();
        if (record.materialized()) {
            // FRO_058: fixture-level guard -- the roster's forceMaterialize already checks
            // record.materialized() before calling in, but the fixture itself had no defense of
            // its own. Without this, any other future caller could re-materialize a live record
            // with a fresh entityId, silently orphaning the old one.
            OUT.warn("[Boss] materialize(): bossId=" + Ids.shortId(bossId) + " is already materialized"
                    + " (entity " + Ids.shortId(record.bossEntityId()) + ") -- ignoring.");
            return false;
        }

        // Border Pregeneration: position was already finalized (and validated) by
        // finalizePosition() before this is ever called -- nothing left to resolve about where
        // the boss stands, only that its entity now exists.
        BossRecord updated = record.materializedAt(entityId, scale);
        bosses.removeIf(r -> r.bossId().equals(bossId));
        bosses.add(updated);
        changed();
        return true;
    }

    /**
     * FRO_160: returns a materialized, alive record to unmaterialized by clearing only
     * {@code bossEntityId} -- {@code alive}, {@code position} and {@code scale}
     * are untouched. The ordinary materialization tick then respawns the boss at
     * {@code position} through the same path as any first materialization (boss.md's "Player
     * credit and non-player death"). Validated like {@link #markDefeated}/{@link #materialize}:
     * a missing, already-defeated, or already-unmaterialized record is warned about and left
     * alone -- the last case also makes a duplicate death event for one physical death a no-op.
     *
     * @return true if the record existed, was alive, and was materialized and now isn't
     */
    public boolean dematerialize(UUID bossId) {
        Satchel.requireServer();

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Boss] dematerialize(): no record for bossId=" + Ids.shortId(bossId) + " -- ignoring.");
            return false;
        }

        BossRecord record = existing.get();
        if (!record.alive()) {
            OUT.warn("[Boss] dematerialize(): bossId=" + Ids.shortId(bossId) + " is already defeated -- ignoring.");
            return false;
        }
        if (!record.materialized()) {
            OUT.warn("[Boss] dematerialize(): bossId=" + Ids.shortId(bossId) + " is not materialized -- ignoring.");
            return false;
        }

        BossRecord updated = record.dematerialized();
        bosses.removeIf(r -> r.bossId().equals(bossId));
        bosses.add(updated);
        changed();
        return true;
    }

    /**
     * Marks a boss record defeated (`alive: false`), addressed by its own {@code bossId} --
     * never via any {@code Border} reference, this fixture isn't keyed by one (see this class's
     * own doc and boss.md's "Data model" section). Replaces the record in place (records are
     * immutable), same remove-then-add-back shape {@link #materialize} uses. RM_FRO_019
     * ("Karen")'s own concern.
     */
    public boolean markDefeated(UUID bossId) {
        Satchel.requireServer();

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Boss] markDefeated(): no record for bossId=" + Ids.shortId(bossId) + " -- ignoring.");
            return false;
        }

        BossRecord record = existing.get();
        if (!record.alive()) {
            // FRO_058: real, shipped bug this guard closes -- BossAPI.forceDefeat() had no check
            // that the record was still alive before running the full grow-and-spawn cascade, so
            // /boss transform defeat run twice against the same boss re-triggered it a second
            // time with no real defeat behind it. See boss.md's "Mutation validation boundary".
            OUT.warn("[Boss] markDefeated(): bossId=" + Ids.shortId(bossId) + " is already defeated -- ignoring.");
            return false;
        }

        BossRecord updated = record.defeated();
        bosses.removeIf(r -> r.bossId().equals(bossId));
        bosses.add(updated);
        changed();
        return true;
    }

    /**
     * Deletes a boss record outright (backing {@code /boss delete}, reached through
     * {@link com.arryn.frontiermode.boss.BossAPI#removeBoss}), then announces the removal to the record's kind
     * ({@link BossKind#onRemoved}), which drops its own record. No paired cleanup is needed here:
     * {@link com.arryn.frontiermode.boss.common.fixture.BossMobFixture} is never persisted and
     * tears down on its own via {@code MobJig}'s reason-agnostic scope teardown if the removed
     * record still had a live entity (see boss.md's "Known limitation" section -- same
     * indistinguishable-from-chunk-unload shape applies here as everywhere else).
     *
     * @return true if a record with this id existed and was removed; false if there was nothing
     *         to remove (not an error -- {@code /boss delete} reports this as a plain failure).
     */
    public boolean remove(UUID bossId) {
        Satchel.requireServer();

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            return false;
        }

        bosses.removeIf(r -> r.bossId().equals(bossId));
        changed();

        Optional<BossKind> kindOpt = BossKinds.get(existing.get().kind());
        if (kindOpt.isEmpty()) {
            OUT.warn("[Boss] remove(): bossId=" + Ids.shortId(bossId) + " names kind \"" + existing.get().kind()
                    + "\", which is not registered -- no kind record to drop.");
        } else {
            kindOpt.get().onRemoved(level(), bossId);
        }
        return true;
    }
}
