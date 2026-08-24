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
 * <p>{@code position}'s Y component is a placeholder (copied from the originating {@code Border}'s
 * own center Y at creation) until materialization resolves a real ground height and produces a
 * fresh record with the true spawn Y -- picking the XZ column and resolving its ground Y are two
 * separate steps, not one (see {@code boss.md}'s "Spawn algorithm" section).
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
        this.position = Objects.requireNonNull(position, "position");
        this.layer = layer;
        this.bossEntityId = bossEntityId; // nullable
        this.alive = alive;
    }

    public UUID bossId() {
        return bossId;
    }

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

    public boolean materialized() {
        return bossEntityId != null;
    }

    /**
     * Returns a copy of this record with {@code bossEntityId}/{@code position} updated to reflect
     * a just-completed materialization (the real ground Y resolved, the vanilla mob spawned).
     * {@code position}'s XZ never changes here -- only Y, which was a placeholder until now.
     */
    public BossRecord materializedAt(BlockPos resolvedPosition, UUID entityId) {
        return new BossRecord(
                bossId,
                Objects.requireNonNull(resolvedPosition, "resolvedPosition"),
                layer,
                Objects.requireNonNull(entityId, "entityId"),
                alive
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
        tag.putInt("x", r.position.getX());
        tag.putInt("y", r.position.getY());
        tag.putInt("z", r.position.getZ());
        tag.putInt("layer", r.layer);
        tag.putBoolean("hasEntity", r.bossEntityId != null);
        if (r.bossEntityId != null) {
            tag.putUUID("bossEntityId", r.bossEntityId);
        }
        tag.putBoolean("alive", r.alive);
        return tag;
    }

    static BossRecord load(CompoundTag tag) {
        UUID entityId = tag.getBoolean("hasEntity") ? tag.getUUID("bossEntityId") : null;
        return new BossRecord(
                tag.getUUID("bossId"),
                new BlockPos(
                        tag.getInt("x"),
                        tag.getInt("y"),
                        tag.getInt("z")
                ),
                tag.getInt("layer"),
                entityId,
                tag.getBoolean("alive")
        );
    }
}
