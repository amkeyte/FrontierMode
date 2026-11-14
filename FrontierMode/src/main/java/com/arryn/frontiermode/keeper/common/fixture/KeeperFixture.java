package com.arryn.frontiermode.keeper.common.fixture;

import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.RecordChange;
import com.arryn.frontiermode.core.SetChange;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Level-scoped ({@code LevelScope}), persisted and networked collection of {@link KeeperRecord}s, and the
 * {@code pendingAttach} set -- the Keeper's own data, in its own {@link
 * com.arryn.frontiermode.keeper.common.bundle.KeeperBundle} on {@code KEEPER_JIG}
 * (wiki/frontiermode/architecture/keeper.md § The Keeper record). Each record extends the base
 * {@code BossRecord} with the same {@code bossId}; the two live in different bundles, so a client receives
 * them as separate parcels, in either order. Every mutation is the server's ({@code Satchel.requireServer()}).
 *
 * <p><b>{@code pendingAttach}</b> is the border ids that are on the path with no Keeper yet: a boss-less
 * path layer produced by a boss-less {@code pathGrow()} or by removing an on-path Keeper
 * (keeper.md § Boss-less path layers and attach). It is a set of its own, since it tracks Borders with no
 * record at all.
 */
public final class KeeperFixture extends SatchelFixture {

    private static final String KEY_KEEPERS = "keepers";
    private static final String KEY_PENDING_ATTACH = "pendingAttach";

    private final List<KeeperRecord> records = new ArrayList<>();
    private final Set<UUID> pendingAttach = new HashSet<>();

    // The last mismatch reportMismatch() logged, so it logs only on a real transition and not every tick the
    // same mismatch persists. KeeperFixture is one per level, so a plain field carries it.
    private Set<Integer> lastReportedMismatch = Set.of();

    public KeeperFixture() {
        registerCustom(KEY_KEEPERS, this::saveRecords, this::loadRecords);
        registerCustom(KEY_PENDING_ATTACH, this::savePendingAttach, this::loadPendingAttach);
    }

    private void saveRecords(CompoundTag root) {
        Satchel.requireServer();

        ListTag list = new ListTag();
        for (KeeperRecord record : records) {
            list.add(saveKeeper(record));
        }
        root.put(KEY_KEEPERS, list);
    }

    private void loadRecords(CompoundTag root) {
        records.clear();
        if (!root.contains(KEY_KEEPERS, Tag.TAG_LIST)) {
            return;
        }

        ListTag list = root.getList(KEY_KEEPERS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            records.add(loadKeeper((CompoundTag) t));
        }
    }

    private static CompoundTag saveKeeper(KeeperRecord r) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("bossId", r.bossId());
        tag.putInt("layer", r.layer());
        tag.putBoolean("hasPathPosition", r.pathPosition().isPresent());
        r.pathPosition().ifPresent(p -> tag.putInt("pathPosition", p));
        tag.putString("body", r.body());
        tag.putBoolean("hasBorderId", r.borderId().isPresent());
        r.borderId().ifPresent(id -> tag.putUUID("borderId", id));
        tag.putBoolean("hasPlatform", r.platformPos() != null);
        if (r.platformPos() != null) {
            tag.putInt("platformX", r.platformPos().getX());
            tag.putInt("platformY", r.platformPos().getY());
            tag.putInt("platformZ", r.platformPos().getZ());
        }
        return tag;
    }

    private static KeeperRecord loadKeeper(CompoundTag tag) {
        return new KeeperRecord(
                tag.getUUID("bossId"),
                tag.getInt("layer"),
                tag.getBoolean("hasPathPosition") ? OptionalInt.of(tag.getInt("pathPosition")) : OptionalInt.empty(),
                tag.getString("body"),
                tag.getBoolean("hasBorderId") ? Optional.of(tag.getUUID("borderId")) : Optional.empty(),
                tag.getBoolean("hasPlatform")
                        ? new BlockPos(tag.getInt("platformX"), tag.getInt("platformY"), tag.getInt("platformZ"))
                        : null
        );
    }

    // The same ListTag-of-UUID persistence shape BordersFixture.borderPath uses
    // (NbtUtils.createUUID/loadUUID over a TAG_INT_ARRAY list).
    private void savePendingAttach(CompoundTag root) {
        Satchel.requireServer();

        ListTag list = new ListTag();
        for (UUID id : pendingAttach) {
            list.add(NbtUtils.createUUID(id));
        }
        root.put(KEY_PENDING_ATTACH, list);
    }

    private void loadPendingAttach(CompoundTag root) {
        pendingAttach.clear();
        if (!root.contains(KEY_PENDING_ATTACH, Tag.TAG_LIST)) {
            return;
        }

        ListTag list = root.getList(KEY_PENDING_ATTACH, Tag.TAG_INT_ARRAY);
        for (Tag t : list) {
            pendingAttach.add(NbtUtils.loadUUID(t));
        }
    }

    @Override
    public void onLoaded() {
        super.onLoaded();
        OUT.info("[Keeper] onLoaded(): Keepers loaded: " + records.size());
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** A snapshot of every Keeper record: a defensive copy, so a caller may iterate while creating more. */
    public List<KeeperRecord> all() {
        return List.copyOf(records);
    }

    public Optional<KeeperRecord> get(UUID bossId) {
        return records.stream()
                .filter(r -> r.bossId().equals(bossId))
                .findFirst();
    }

    /**
     * Every {@code layer} value currently on record -- the path reconciliation's input (compared against the
     * path's set of {@code Border.layer()} values). One record per layer is the expected common case, but this
     * returns every value, since a layer on the path with no record here is exactly what the check surfaces.
     */
    public Set<Integer> layers() {
        return records.stream()
                .map(KeeperRecord::layer)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether any Keeper record sharing {@code borderId} is still alive, per the base records in
     * {@code bosses} -- the "last one standing" half of cascade gating (keeper.md § Cascade gating). No
     * explicit exclusion of a just-defeated Keeper is needed: a caller asks this right after its own
     * {@code markDefeated} landed, so that record's {@code alive} is already false and only a separate sibling
     * on the same {@code borderId} can make this true.
     */
    public boolean anyAliveWithBorderId(BossFixture bosses, UUID borderId) {
        Objects.requireNonNull(borderId, "borderId");
        return records.stream()
                .filter(r -> r.borderId().map(borderId::equals).orElse(false))
                .anyMatch(r -> bosses.get(r.bossId()).map(BossRecord::alive).orElse(false));
    }

    /**
     * The first Keeper record whose {@code borderId} is {@code borderId}, alive or defeated, materialized or not: what
     * a Border change request asks (keeper.md § A Keeper holds its Border still). More than one record can share a
     * Border (the cascade gating exists for that) and any one of them holds it, so which one comes back does not
     * matter. Unlike {@link #anyAliveWithBorderId} it reads no base record and counts a defeated one.
     */
    public Optional<KeeperRecord> firstForBorder(UUID borderId) {
        Objects.requireNonNull(borderId, "borderId");
        return records.stream()
                .filter(r -> r.borderId().map(borderId::equals).orElse(false))
                .findFirst();
    }

    /** Read-only snapshot, the same defensive-copy discipline {@link #all()} follows. */
    public Set<UUID> pendingAttach() {
        return Set.copyOf(pendingAttach);
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    /**
     * Marks the fixture dirty and pushes it to clients now, so a Keeper's record reaches a client with its
     * base record instead of up to the sync interval later.
     */
    private void changed() {
        markDirty();
        getBundle().forceSync();
    }

    /** Adds a record. The Keeper kind's {@code onCreate} is the one caller. */
    public void add(KeeperRecord record) {
        Satchel.requireServer();
        Objects.requireNonNull(record, "record");

        records.add(record);
        changed();
    }

    /** Drops the record for {@code bossId}; returns it, or empty if there was none. */
    public Optional<KeeperRecord> remove(UUID bossId) {
        Satchel.requireServer();

        Optional<KeeperRecord> existing = get(bossId);
        if (existing.isPresent()) {
            records.remove(existing.get());
            changed();
        }
        return existing;
    }

    /**
     * Records that the Keeper's spawn platform is built, with its base position. Once per record: a second
     * call for a record that already has one is a logged no-op, so a respawned Keeper returns onto the
     * platform already there and it is never re-carved. {@link RecordChange#NO_RECORD} for a missing record,
     * {@code WRONG_STATE} for one that already has a platform.
     */
    public RecordChange setPlatform(UUID bossId, BlockPos platformPos) {
        Satchel.requireServer();
        Objects.requireNonNull(platformPos, "platformPos");

        Optional<KeeperRecord> existing = get(bossId);
        if (existing.isEmpty()) {
            OUT.warn("[Keeper] setPlatform(): no record for bossId=" + Ids.shortId(bossId) + " -- ignoring.");
            return RecordChange.NO_RECORD;
        }
        if (existing.get().platformBuilt()) {
            OUT.warn("[Keeper] setPlatform(): bossId=" + Ids.shortId(bossId) + " already has a platform -- ignoring.");
            return RecordChange.WRONG_STATE;
        }

        records.remove(existing.get());
        records.add(existing.get().withPlatform(platformPos));
        markDirty();
        return RecordChange.DONE;
    }

    /**
     * Marks {@code borderId} as awaiting {@code /boss attach} -- written by
     * {@code BorderCommandHandler.pathGrow()} on a successful boss-less grow, and by the Keeper kind's
     * {@code onRemoved} when the removed Keeper was on-path.
     *
     * @return {@link SetChange#CHANGED} if {@code borderId} was newly added; {@link SetChange#UNCHANGED} if it was
     *         already pending (a plain no-op, not an error -- {@code Set.add}'s own idempotent contract).
     */
    public SetChange addPendingAttach(UUID borderId) {
        Satchel.requireServer();
        Objects.requireNonNull(borderId, "borderId");

        if (!pendingAttach.add(borderId)) {
            return SetChange.UNCHANGED;
        }
        markDirty();
        return SetChange.CHANGED;
    }

    /**
     * Clears {@code borderId} from the pending set -- written by {@code /boss attach} once it has created and
     * paired a real record for that border.
     *
     * @return {@link SetChange#CHANGED} if {@code borderId} was present and removed; {@link SetChange#UNCHANGED} if it
     *         wasn't pending (a plain no-op, not an error).
     */
    public SetChange removePendingAttach(UUID borderId) {
        Satchel.requireServer();
        Objects.requireNonNull(borderId, "borderId");

        if (!pendingAttach.remove(borderId)) {
            return SetChange.UNCHANGED;
        }
        markDirty();
        return SetChange.CHANGED;
    }

    // ------------------------------------------------------------------
    // Path reconciliation
    // ------------------------------------------------------------------

    /**
     * The edge-triggered compare-and-log between the path's layers and the Keeper records' layers. Resolving
     * {@code BorderAPI.PATH}/{@code CRUD} and walking the path to build {@code pathLayers} (layer value ->
     * that layer's border id) is the caller's; this method compares it against {@link #layers()} and logs only
     * on a real transition, not every tick the same mismatch persists.
     *
     * <p>A gap whose border id is in {@link #pendingAttach()} logs at {@code OUT.info} ("awaiting attach",
     * the sanctioned wait-state a boss-less {@code pathGrow()} or an on-path {@code /boss delete} produces)
     * instead of the loud {@code OUT.warn} "real data bug"; an uncovered gap logs as a warning. Both halves feed
     * the same edge trigger, keyed on the full {@code missing} set, so a gap closing, or moving from uncovered
     * to covered by a fresh {@code pendingAttach} entry, still logs once.
     */
    public void reportMismatch(Level level, Map<Integer, UUID> pathLayers) {
        Set<Integer> missing = new HashSet<>(pathLayers.keySet());
        missing.removeAll(layers());

        if (missing.equals(lastReportedMismatch)) {
            return;
        }

        if (missing.isEmpty()) {
            OUT.info("[Keeper] reportMismatch(): previously-mismatched layer(s) " + lastReportedMismatch
                    + " no longer mismatched in level " + level.dimension().location() + ".");
            lastReportedMismatch = Set.of();
            return;
        }

        Set<UUID> pending = pendingAttach();
        Set<Integer> awaitingAttach = new HashSet<>();
        Set<Integer> uncovered = new HashSet<>();
        for (Integer layer : missing) {
            UUID borderId = pathLayers.get(layer);
            if (borderId != null && pending.contains(borderId)) {
                awaitingAttach.add(layer);
            } else {
                uncovered.add(layer);
            }
        }

        if (!awaitingAttach.isEmpty()) {
            OUT.info("[Keeper] reportMismatch(): path layer(s) " + awaitingAttach + " have no Keeper record in"
                    + " level " + level.dimension().location() + " -- awaiting /boss attach (pendingAttach),"
                    + " not a data bug.");
        }
        if (!uncovered.isEmpty()) {
            OUT.warn("[Keeper] reportMismatch(): path has border(s) at layer(s) " + uncovered
                    + " with no matching KeeperFixture record in level " + level.dimension().location()
                    + " -- real data bug (missed call site, crash between paired calls, or manual world"
                    + " editing), not a normal transient state.");
        }
        lastReportedMismatch = Set.copyOf(missing);
    }
}
