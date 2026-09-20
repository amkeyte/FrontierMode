package com.arryn.frontiermode.structure.common;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable per-Cairn record -- {@code CairnFixture} holds a flat collection of these, one per
 * boss, the same "one fixture, many logical sub-entities" shape {@code BossFixture} already uses
 * for bosses. See wiki/frontiermode/architecture/cairn.md#data-model.
 *
 * <p>{@code anchorPos} starts {@code null} -- the record exists (a qualifying boss has a Cairn
 * coming) before Cairn has found and validated its own spot. {@code lastKnownBossPos} is seeded
 * from the boss's own {@code position()} at record-creation time and is never null -- creation
 * itself only ever happens once that position has already finalized (see
 * {@code BossPositionListeners}).
 */
public final class CairnRecord {

    private final UUID bossId;
    private final BlockPos anchorPos;
    private final BlockPos lastKnownBossPos;

    public CairnRecord(UUID bossId, BlockPos anchorPos, BlockPos lastKnownBossPos) {
        this.bossId = Objects.requireNonNull(bossId, "bossId");
        this.anchorPos = anchorPos; // nullable -- see class doc
        this.lastKnownBossPos = Objects.requireNonNull(lastKnownBossPos, "lastKnownBossPos");
    }

    public UUID bossId() {
        return bossId;
    }

    /** Nullable -- see class doc. Check {@link #placed()} first. */
    public BlockPos anchorPos() {
        return anchorPos;
    }

    public boolean placed() {
        return anchorPos != null;
    }

    public BlockPos lastKnownBossPos() {
        return lastKnownBossPos;
    }

    public CairnRecord withAnchor(BlockPos newAnchorPos) {
        return new CairnRecord(bossId, Objects.requireNonNull(newAnchorPos, "newAnchorPos"), lastKnownBossPos);
    }

    public CairnRecord withLastKnownBossPos(BlockPos newLastKnownBossPos) {
        return new CairnRecord(bossId, anchorPos, Objects.requireNonNull(newLastKnownBossPos, "newLastKnownBossPos"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CairnRecord other)) return false;
        return bossId.equals(other.bossId);
    }

    @Override
    public int hashCode() {
        return bossId.hashCode();
    }

    static CompoundTag save(CairnRecord r) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("bossId", r.bossId);
        tag.putBoolean("hasAnchor", r.anchorPos != null);
        if (r.anchorPos != null) {
            tag.putInt("ax", r.anchorPos.getX());
            tag.putInt("ay", r.anchorPos.getY());
            tag.putInt("az", r.anchorPos.getZ());
        }
        tag.putInt("lx", r.lastKnownBossPos.getX());
        tag.putInt("ly", r.lastKnownBossPos.getY());
        tag.putInt("lz", r.lastKnownBossPos.getZ());
        return tag;
    }

    static CairnRecord load(CompoundTag tag) {
        BlockPos anchor = tag.getBoolean("hasAnchor")
                ? new BlockPos(tag.getInt("ax"), tag.getInt("ay"), tag.getInt("az"))
                : null;
        BlockPos lastKnown = new BlockPos(tag.getInt("lx"), tag.getInt("ly"), tag.getInt("lz"));
        return new CairnRecord(tag.getUUID("bossId"), anchor, lastKnown);
    }
}
