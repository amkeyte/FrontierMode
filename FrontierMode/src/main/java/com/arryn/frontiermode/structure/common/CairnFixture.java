package com.arryn.frontiermode.structure.common;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * {@code LevelScope}-hosted, persisted collection of {@link CairnRecord}s -- FRO_120's build
 * against wiki/frontiermode/architecture/cairn.md#data-model. Real persistence wired explicitly
 * by {@code StructureModule} (see cairn.md's "Real persistence has to be wired explicitly"
 * note) -- this class itself just registers the field, the same {@code registerCustom} shape
 * {@code BossFixture} already establishes.
 */
public final class CairnFixture extends SatchelFixture {

    private static final String KEY_CAIRNS = "cairns";

    private final List<CairnRecord> cairns = new ArrayList<>();

    // Transient, not persisted -- StructureModule's own "same fixed candidate, never a new
    // random guess" while waiting on a chunk to load (cairn.md's own wording for the identical
    // Boss materialization precedent). Losing this across a restart just means a fresh candidate
    // gets resampled -- an acceptable, rare edge case, not a correctness requirement worth
    // persisting for.
    private final Map<UUID, BlockPos> pendingCandidates = new HashMap<>();

    public BlockPos pendingCandidate(UUID bossId) {
        return pendingCandidates.get(bossId);
    }

    public void setPendingCandidate(UUID bossId, BlockPos pos) {
        pendingCandidates.put(bossId, pos);
    }

    public void clearPendingCandidate(UUID bossId) {
        pendingCandidates.remove(bossId);
    }

    public CairnFixture() {
        registerCustom(KEY_CAIRNS, this::saveCairns, this::loadCairns);
    }

    public List<CairnRecord> all() {
        return List.copyOf(cairns);
    }

    public Optional<CairnRecord> get(UUID bossId) {
        return cairns.stream().filter(c -> c.bossId().equals(bossId)).findFirst();
    }

    /** Finds the Cairn (if any) whose committed {@code anchorPos} matches {@code pos} exactly. */
    public Optional<CairnRecord> getByAnchor(BlockPos pos) {
        return cairns.stream().filter(c -> pos.equals(c.anchorPos())).findFirst();
    }

    /**
     * Creates a fresh, unplaced record for {@code bossId} if none exists yet -- idempotent, same
     * "if absent" shape {@code BossTellFixture.createTellCurveIfAbsent}/{@code
     * BossSentinelsFixture.ensureSentinelCurves} already establish for paired-creation call
     * sites. Returns the existing record unchanged if one is already there (defends against
     * {@code BossPositionListeners} somehow firing twice for the same boss).
     *
     * <p>[Arryn] examine why the Result value is never used.
     *
     */
    @SuppressWarnings("UnusedReturnValue")
    public CairnRecord createIfAbsent(UUID bossId, BlockPos lastKnownBossPos) {
        Optional<CairnRecord> existing = get(bossId);
        if (existing.isPresent()) {
            return existing.get();
        }
        CairnRecord record = new CairnRecord(bossId, null, lastKnownBossPos);
        cairns.add(record);
        markDirty();
        return record;
    }

    public void updateAnchor(UUID bossId, BlockPos anchorPos) {
        replace(bossId, r -> r.withAnchor(anchorPos));
    }

    public void updateLastKnownBossPos(UUID bossId, BlockPos pos) {
        replace(bossId, r -> r.withLastKnownBossPos(pos));
    }

    private void replace(UUID bossId, UnaryOperator<CairnRecord> fn) {
        for (int i = 0; i < cairns.size(); i++) {
            if (cairns.get(i).bossId().equals(bossId)) {
                cairns.set(i, fn.apply(cairns.get(i)));
                markDirty();
                return;
            }
        }
    }

    private void saveCairns(CompoundTag root) {
        Satchel.requireServer();

        ListTag list = new ListTag();
        for (CairnRecord record : cairns) {
            list.add(CairnRecord.save(record));
        }
        root.put(KEY_CAIRNS, list);
    }

    private void loadCairns(CompoundTag root) {
        if (!root.contains(KEY_CAIRNS, Tag.TAG_LIST)) {
            return;
        }
        cairns.clear();

        ListTag list = root.getList(KEY_CAIRNS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            try {
                cairns.add(CairnRecord.load((CompoundTag) t));
            } catch (RuntimeException e) {
                OUT.warn("[Structure] Skipping malformed Cairn record during load: " + e);
            }
        }
    }
}
