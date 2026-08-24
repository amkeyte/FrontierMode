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
import java.util.Collections;
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

    public List<BossRecord> all() {
        return Collections.unmodifiableList(bosses);
    }

    public Optional<BossRecord> get(UUID bossId) {
        return bosses.stream()
                .filter(r -> r.bossId().equals(bossId))
                .findFirst();
    }

    /**
     * Records with {@code alive: true} and {@code bossEntityId == null} -- not yet materialized.
     * {@code BOSS_JIG}'s own tick walks this every cycle (see {@code boss.md}'s "Three questions,
     * three different mechanisms").
     */
    public List<BossRecord> unmaterialized() {
        return bosses.stream()
                .filter(r -> r.alive() && !r.materialized())
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
     * Creates a new, unmaterialized boss record -- the direct call paired at a real
     * border-creation call site (see {@code boss.md}'s "Defeat detection and the border-growth
     * gap"). {@code position}'s Y is a placeholder until materialization resolves real ground
     * height; see {@link BossRecord}'s own docs.
     */
    public BossRecord create(BlockPos position, int layer) {
        requireServerSide();
        Objects.requireNonNull(position, "position");

        BossRecord record = new BossRecord(
                UUID.randomUUID(),
                position,
                layer,
                null,
                true
        );

        bosses.add(record);
        markDirty();
        return record;
    }

    /**
     * Records materialization: the vanilla mob has been spawned at a resolved ground position and
     * tagged via {@code MobScope.getFor(mob)}. Replaces the record in place (records are
     * immutable) -- same remove-then-add-back shape {@code BordersFixture.reassignLayers} uses.
     */
    public void materialize(UUID bossId, BlockPos resolvedPosition, UUID entityId) {
        requireServerSide();

        Optional<BossRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Boss] materialize(): no record for bossId=" + bossId + " -- ignoring.");
            return;
        }

        BossRecord updated = existing.get().materializedAt(resolvedPosition, entityId);
        bosses.removeIf(r -> r.bossId().equals(bossId));
        bosses.add(updated);
        markDirty();
    }

    private void requireServerSide() {
        if (Satchel.require().side() == LogicalSide.CLIENT) {
            throw new IllegalStateException();
        }
    }
}
