package com.arryn.frontiermode.boss.common.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable per-boss record for {@link BossTellFixture} -- tracks each boss's bedrock-platform
 * state. {@code platformPos} is null until the 3×3 bedrock platform has been placed for this
 * boss's position; once set via {@link BossTellFixture}'s own tick (when the boss chunk is
 * first loaded), it is never changed again.
 *
 * <p>Mirrors {@link BossRecord}'s own persistence shape (a {@code has} boolean alongside a
 * nullable field) so loading pre-platform records from disk is backwards-compatible by
 * construction.
 */
public final class BossTellRecord {

    private final UUID bossId;
    private final BlockPos platformPos; // nullable until the bedrock platform is placed

    public BossTellRecord(UUID bossId, BlockPos platformPos) {
        this.bossId = Objects.requireNonNull(bossId, "bossId");
        this.platformPos = platformPos; // nullable -- see class doc
    }

    public UUID bossId() {
        return bossId;
    }

    /**
     * Nullable -- null until the bedrock platform has been placed. Check {@link #hasPlatform()}
     * before calling.
     */
    public BlockPos platformPos() {
        return platformPos;
    }

    public boolean hasPlatform() {
        return platformPos != null;
    }

    /** Returns a copy of this record with {@code platformPos} set to {@code pos}. */
    public BossTellRecord withPlatform(BlockPos pos) {
        return new BossTellRecord(bossId, Objects.requireNonNull(pos, "pos"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BossTellRecord other)) return false;
        return bossId.equals(other.bossId);
    }

    @Override
    public int hashCode() {
        return bossId.hashCode();
    }

    static CompoundTag save(BossTellRecord r) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("bossId", r.bossId);
        tag.putBoolean("hasPlatform", r.platformPos != null);
        if (r.platformPos != null) {
            tag.putInt("platformX", r.platformPos.getX());
            tag.putInt("platformY", r.platformPos.getY());
            tag.putInt("platformZ", r.platformPos.getZ());
        }
        return tag;
    }

    static BossTellRecord load(CompoundTag tag) {
        UUID bossId = tag.getUUID("bossId");
        BlockPos platformPos = tag.getBoolean("hasPlatform")
                ? new BlockPos(tag.getInt("platformX"), tag.getInt("platformY"), tag.getInt("platformZ"))
                : null;
        return new BossTellRecord(bossId, platformPos);
    }
}
