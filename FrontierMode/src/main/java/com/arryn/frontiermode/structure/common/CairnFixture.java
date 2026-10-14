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
 *
 * <p>A boss can have several Cairns (FRO_226), so everything here is keyed by a record's own
 * {@code cairnId}, never by {@code bossId}.
 */
public final class CairnFixture extends SatchelFixture {

    private static final String KEY_CAIRNS = "cairns";

    private final List<CairnRecord> cairns = new ArrayList<>();

    // Transient, not persisted: how many candidate spots each Cairn has had rejected so far. Losing it
    // across a restart just gives the Cairn a fresh allowance -- not worth persisting.
    private final Map<UUID, Integer> failedCandidates = new HashMap<>();

    public CairnFixture() {
        registerCustom(KEY_CAIRNS, this::saveCairns, this::loadCairns);
    }

    public List<CairnRecord> all() {
        return List.copyOf(cairns);
    }

    public Optional<CairnRecord> get(UUID cairnId) {
        return cairns.stream().filter(c -> c.cairnId().equals(cairnId)).findFirst();
    }

    /** Every Cairn of {@code bossId}, in the order they were created. */
    public List<CairnRecord> forBoss(UUID bossId) {
        return cairns.stream().filter(c -> c.bossId().equals(bossId)).toList();
    }

    /** Finds the Cairn (if any) whose committed {@code anchorPos} matches {@code pos} exactly. */
    public Optional<CairnRecord> getByAnchor(BlockPos pos) {
        return cairns.stream().filter(c -> pos.equals(c.anchorPos())).findFirst();
    }

    /**
     * Creates {@code count} fresh, unplaced records for {@code bossId}, all at once -- unless the
     * boss already has any, in which case nothing is created and the existing ones are returned
     * (defends against {@code BossPositionListeners} somehow firing twice for the same boss). Returns
     * the records now held for the boss.
     */
    public List<CairnRecord> createForBoss(UUID bossId, BlockPos lastKnownBossPos, int count) {
        List<CairnRecord> existing = forBoss(bossId);
        if (!existing.isEmpty() || count <= 0) {
            return existing;
        }
        for (int i = 0; i < count; i++) {
            cairns.add(new CairnRecord(UUID.randomUUID(), bossId, null, lastKnownBossPos));
        }
        markDirty();
        return forBoss(bossId);
    }

    /** Commits {@code cairnId}'s spot; its rejected-candidate count no longer matters. */
    public void updateAnchor(UUID cairnId, BlockPos anchorPos) {
        failedCandidates.remove(cairnId);
        replace(cairnId, r -> r.withAnchor(anchorPos));
    }

    public void updateLastKnownBossPos(UUID cairnId, BlockPos pos) {
        replace(cairnId, r -> r.withLastKnownBossPos(pos));
    }

    /** Counts one more rejected candidate for {@code cairnId} and returns the new total. */
    public int recordFailedCandidate(UUID cairnId) {
        return failedCandidates.merge(cairnId, 1, Integer::sum);
    }

    /** Drops {@code cairnId}'s record -- a Cairn that gave up finding ground. */
    public void remove(UUID cairnId) {
        failedCandidates.remove(cairnId);
        if (cairns.removeIf(c -> c.cairnId().equals(cairnId))) {
            markDirty();
        }
    }

    private void replace(UUID cairnId, UnaryOperator<CairnRecord> fn) {
        for (int i = 0; i < cairns.size(); i++) {
            if (cairns.get(i).cairnId().equals(cairnId)) {
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
