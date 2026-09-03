package com.arryn.frontiermode.boss.common.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable materialized boss record -- {@link BossFixture}'s sole durable source of truth for
 * boss identity. Not keyed by, and holding no live reference to, any {@code Border} at all: see
 * wiki/frontiermode/architecture/boss.md's "Data model" section for why this decoupling is
 * deliberate. {@code layer} is a plain copy of the originating {@code Border.layer()} taken once
 * at creation, never re-read afterward.
 *
 * <p><b>{@code position} is nullable</b> -- null until finalized, per
 * wiki/frontiermode/architecture/border-pregeneration.md#what-this-changes-in-boss, which
 * supersedes this class's earlier claim that position is picked once, immediately, at
 * creation. A record starts as {@code {position: null, layer, bossEntityId: null, alive: true}}
 * and stays that way until {@code BOSS_JIG}'s own tick finalizes a real position once
 * {@code BorderAPI.isPregenReady()} passes for its home border -- see {@code boss.md}'s "Three
 * questions, three different mechanisms" section. Once finalized, {@code position} is already
 * validated, real terrain (flatness/hazard-scored) -- no further Y-resolution ever happens after
 * that point.
 *
 * <p>{@code bossEntityId} is nullable -- null until an entity has actually been placed in the
 * world for this record. {@code alive} is false once defeated (RM_FRO_019's own concern; this
 * node never sets it false).
 */
public final class BossRecord {

    private final UUID bossId;
    private final BlockPos position;
    private final int layer;
    private final UUID bossEntityId;
    private final boolean alive;

    public BossRecord(
            UUID bossId,
            BlockPos position,
            int layer,
            UUID bossEntityId,
            boolean alive
    ) {
        this.bossId = Objects.requireNonNull(bossId, "bossId");
        this.position = position; // nullable -- see this class's own doc
        this.layer = layer;
        this.bossEntityId = bossEntityId; // nullable
        this.alive = alive;
    }

    public UUID bossId() {
        return bossId;
    }
    public String displayBossId()
    {
        String bStr = bossId.toString();
        return bStr.substring(bStr.length()-8);
    }

    /**
     * Nullable -- see this class's own doc. Check {@link #positioned()} first.
     */
    public BlockPos position() {
        return position;
    }

    public int layer() {
        return layer;
    }

    public UUID bossEntityId() {
        return bossEntityId;
    }

    public boolean alive() {
        return alive;
    }

    public boolean positioned() {
        return position != null;
    }

    public boolean materialized() {
        return bossEntityId != null;
    }

    /**
     * Returns a copy of this record with {@code position} set to {@code resolvedPosition} --
     * Border Pregeneration's "finalizing position" step (see this class's own doc). Rejects
     * being called on an already-positioned record via the caller's own guard
     * ({@code BossFixture.finalizePosition}), not here -- this method just performs the copy.
     */
    public BossRecord finalizedAt(BlockPos resolvedPosition) {
        return new BossRecord(
                bossId,
                Objects.requireNonNull(resolvedPosition, "resolvedPosition"),
                layer,
                bossEntityId,
                alive
        );
    }

    /**
     * Returns a copy of this record with {@code bossEntityId} updated to reflect a
     * just-completed materialization (the vanilla mob spawned at this record's already-finalized
     * {@code position}). {@code position} itself never changes here -- Border Pregeneration
     * moved all position resolution to {@link #finalizedAt}, so materialization has nothing left
     * to resolve about where the boss stands.
     */
    public BossRecord materializedAt(UUID entityId) {
        return new BossRecord(
                bossId,
                position,
                layer,
                Objects.requireNonNull(entityId, "entityId"),
                alive
        );
    }

    /**
     * Returns a copy of this record with {@code alive} set to {@code false} -- the defeat
     * transition RM_FRO_019 ("Karen")'s own handler drives, addressed by this record's own
     * {@code bossId} rather than any {@code Border} reference. Position/layer/entity id are
     * unchanged; only the alive flag flips.
     */
    public BossRecord defeated() {
        return new BossRecord(
                bossId,
                position,
                layer,
                bossEntityId,
                false
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BossRecord other)) return false;
        return bossId.equals(other.bossId);
    }

    @Override
    public int hashCode() {
        return bossId.hashCode();
    }

    static CompoundTag save(BossRecord r) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("bossId", r.bossId);
        tag.putBoolean("hasPosition", r.position != null);
        if (r.position != null) {
            tag.putInt("x", r.position.getX());
            tag.putInt("y", r.position.getY());
            tag.putInt("z", r.position.getZ());
        }
        tag.putInt("layer", r.layer);
        tag.putBoolean("hasEntity", r.bossEntityId != null);
        if (r.bossEntityId != null) {
            tag.putUUID("bossEntityId", r.bossEntityId);
        }
        tag.putBoolean("alive", r.alive);
        return tag;
    }

    static BossRecord load(CompoundTag tag) {
        BlockPos position = tag.getBoolean("hasPosition")
                ? new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"))
                : null;
        UUID entityId = tag.getBoolean("hasEntity") ? tag.getUUID("bossEntityId") : null;
        return new BossRecord(
                tag.getUUID("bossId"),
                position,
                tag.getInt("layer"),
                entityId,
                tag.getBoolean("alive")
        );
    }
}
