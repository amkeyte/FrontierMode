package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fml.LogicalSide;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Level-scoped ({@code LevelScope}), persisted collection of {@link BossRecord}s -- the sole
 * durable source of truth for boss identity, per wiki/frontiermode/architecture/boss.md's "Data
 * model" section. Lives in its own {@link com.arryn.frontiermode.boss.common.bundle.BossBundle},
 * not folded into {@code BordersBundle} -- see that page and
 * wiki/satchel/architecture/new-module-checklist.md item 7 for why.
 *
 * <p>Deliberately not split into facets the way {@code BordersFixture} is: Boss's own surface
 * (create, list, materialize) has none of the ordering/rules/CRUD complexity that motivated
 * Border's {@code PATH}/{@code CRUD}/{@code RULES}/{@code INFO} split, so this stays a single
 * fixture with direct methods.
 */
public final class BossFixture extends SatchelFixture {

    private static final String KEY_BOSSES = "bosses";

    private final List<BossRecord> bosses = new ArrayList<>();

    public BossFixture() {
        registerCustom(
                KEY_BOSSES,
                this::saveBosses,
                this::loadBosses
        );
    }

    private void saveBosses(CompoundTag root) {
        requireServerSide();

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
            // Same discipline BordersFixture.loadBorders follows (RM_FRO_013): a single malformed
            // record shouldn't abort hydration of every other boss on this level.
            try {
                bosses.add(BossRecord.load((CompoundTag) t));
            } catch (RuntimeException e) {
                OUT.warn("[Boss] Skipping malformed boss record during load: " + e);
            }
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
                .collect(Collectors.toUnmodifiableList());
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
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Every {@code layer} value currently on record -- the defensive-reconciliation check's own
     * input (compared against the path's set of {@code Border.layer()} values). One record per
     * layer is the expected common case, but this returns every value, duplicates included, since
     * a genuine data bug (a missed call site, a crash between paired calls) is exactly what a
     * layer appearing on the path but never here is meant to surface.
     */
    public Set<Integer> layers() {
        return bosses.stream()
                .map(BossRecord::layer)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    /**
     * Creates a new, unpositioned boss record -- the direct call paired at a real
     * border-creation call site (see {@code boss.md}'s "Defeat detection and the border-growth
     * gap"). {@code position} starts null; see {@link BossRecord}'s own docs and
     * {@link #finalizePosition} for how it's set.
     *
     * <p><b>FRO_058:</b> rejects {@code layer < 0}, mirroring
     * {@code BordersCrudFacet.failureReason()}'s identical check on {@code layerIndex} -- see
     * boss.md's "Mutation validation boundary" section for the ruling. Returns
     * {@code Optional<BossRecord>} (empty on rejection, logged via {@code OUT.warn}) rather than
     * throwing, matching the {@code Optional}/{@code boolean} idiom already used elsewhere on
     * this fixture.
     */
    public Optional<BossRecord> create(int layer) {
        requireServerSide();

        if (layer < 0) {
            OUT.warn("[Boss] create(): rejected -- negative layer " + layer + ".");
            return Optional.empty();
        }

        // Border Pregeneration: position starts unset -- BOSS_JIG's own tick finalizes it once
        // BorderAPI.isPregenReady() passes for this record's home border. See boss.md's "Three
        // questions, three different mechanisms" section.
        BossRecord record = new BossRecord(
                UUID.randomUUID(),
                null,
                layer,
                null,
                true
        );

        bosses.add(record);
        markDirty();
        return Optional.of(record);
    }

    /**
     * Finalizes an unpositioned record's {@code position} -- Border Pregeneration's own new
     * mutation, called only once {@code BorderAPI.isPregenReady()} has passed for the record's
     * home border. Rejects a record that already has a finalized position (defensive, mirroring
     * {@link #materialize}'s identical guard) -- returns {@code false} rather than silently
     * overwriting a committed position.
     */
    public boolean finalizePosition(UUID bossId, BlockPos resolvedPosition) {
        requireServerSide();
        Objects.requireNonNull(resolvedPosition, "resolvedPosition");

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Boss] finalizePosition(): no record for bossId=" + bossId + " -- ignoring.");
            return false;
        }

        BossRecord record = existing.get();
        if (record.positioned()) {
            OUT.warn("[Boss] finalizePosition(): bossId=" + bossId + " already has a finalized"
                    + " position (" + record.position() + ") -- ignoring.");
            return false;
        }

        BossRecord updated = record.finalizedAt(resolvedPosition);
        bosses.removeIf(r -> r.bossId().equals(bossId));
        bosses.add(updated);
        markDirty();
        return true;
    }

    /**
     * Records materialization: the vanilla mob has been spawned at a resolved ground position and
     * tagged via {@code MobScope.getFor(mob)}. Replaces the record in place (records are
     * immutable) -- same remove-then-add-back shape {@code BordersFixture.reassignLayers} uses.
     */
    public boolean materialize(UUID bossId, UUID entityId) {
        requireServerSide();

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Boss] materialize(): no record for bossId=" + bossId + " -- ignoring.");
            return false;
        }

        BossRecord record = existing.get();
        if (record.materialized()) {
            // FRO_058: fixture-level guard -- BossModule.forceMaterialize already checks
            // record.materialized() before calling in, but the fixture itself had no defense of
            // its own. Without this, any other future caller could re-materialize a live record
            // with a fresh entityId, silently orphaning the old one.
            OUT.warn("[Boss] materialize(): bossId=" + bossId + " is already materialized"
                    + " (entity " + record.bossEntityId() + ") -- ignoring.");
            return false;
        }

        // Border Pregeneration: position was already finalized (and validated) by
        // finalizePosition() before this is ever called -- nothing left to resolve about where
        // the boss stands, only that its entity now exists.
        BossRecord updated = record.materializedAt(entityId);
        bosses.removeIf(r -> r.bossId().equals(bossId));
        bosses.add(updated);
        markDirty();
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
        requireServerSide();

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Boss] markDefeated(): no record for bossId=" + bossId + " -- ignoring.");
            return false;
        }

        BossRecord record = existing.get();
        if (!record.alive()) {
            // FRO_058: real, shipped bug this guard closes -- BossAPI.forceDefeat() had no check
            // that the record was still alive before running the full grow-and-spawn cascade, so
            // /boss transform defeat run twice against the same boss re-triggered it a second
            // time with no real defeat behind it. See boss.md's "Mutation validation boundary".
            OUT.warn("[Boss] markDefeated(): bossId=" + bossId + " is already defeated -- ignoring.");
            return false;
        }

        BossRecord updated = record.defeated();
        bosses.removeIf(r -> r.bossId().equals(bossId));
        bosses.add(updated);
        markDirty();
        return true;
    }

    /**
     * Deletes a boss record outright -- FRO_057's net-new fixture method, the one this build
     * actually needs underneath (backing {@code /boss delete}). No paired cleanup required:
     * {@link com.arryn.frontiermode.boss.common.fixture.BossMobFixture} is never persisted and
     * tears down on its own via {@code MobJig}'s reason-agnostic scope teardown if the removed
     * record still had a live entity (see boss.md's "Known limitation" section -- same
     * indistinguishable-from-chunk-unload shape applies here as everywhere else).
     *
     * @return true if a record with this id existed and was removed; false if there was nothing
     *         to remove (not an error -- {@code /boss delete} reports this as a plain failure).
     */
    public boolean remove(UUID bossId) {
        requireServerSide();

        boolean removed = bosses.removeIf(r -> r.bossId().equals(bossId));
        if (removed) {
            markDirty();
        }
        return removed;
    }

    private void requireServerSide() {
        if (Satchel.require().side() == LogicalSide.CLIENT) {
            throw new IllegalStateException();
        }
    }
}
