package com.arryn.frontiermode.boss.common.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable base boss record -- {@link BossFixture}'s sole durable source of truth for boss identity
 * (wiki/frontiermode/architecture/boss.md § The base record). Every boss has one. A kind that needs more keeps
 * a record of its own, in its own module and bundle, under the same {@code bossId}; nothing here names a
 * Border, a layer or a path position.
 *
 * <p><b>{@code position} is nullable</b>: unset until the kind says it is final. <b>{@code bossEntityId} is
 * nullable</b>: unset until the mob is placed, and again after a non-player death. {@code alive} is false once
 * defeated. {@code scale} is the body size multiplier, 1.0 until the kind sets it at materialize; the server's
 * hitbox and the client's hitbox and render both read it.
 */
public final class BossRecord {

    private final UUID bossId;
    private final String kind;
    private final BlockPos position; // nullable until finalized
    private final UUID bossEntityId; // nullable until materialized
    private final boolean alive;
    private final float scale;

    public BossRecord(
            UUID bossId,
            String kind,
            BlockPos position,
            UUID bossEntityId,
            boolean alive,
            float scale
    ) {
        this.bossId = Objects.requireNonNull(bossId, "bossId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.position = position; // nullable -- see this class's own doc
        this.bossEntityId = bossEntityId; // nullable
        this.alive = alive;
        this.scale = scale;
    }

    public UUID bossId() {
        return bossId;
    }

    public String displayBossId() {
        String bStr = bossId.toString();
        return bStr.substring(bStr.length() - 8);
    }

    /** The key of the {@code BossKind} that owns this boss. */
    public String kind() {
        return kind;
    }

    /**
     * Nullable -- see this class's own doc. Check {@link #positioned()} first.
     */
    public BlockPos position() {
        return position;
    }

    public UUID bossEntityId() {
        return bossEntityId;
    }

    public boolean alive() {
        return alive;
    }

    /** The body size multiplier: 1.0 until the kind sets it at {@link #materializedAt}. */
    public float scale() {
        return scale;
    }

    public boolean positioned() {
        return position != null;
    }

    public boolean materialized() {
        return bossEntityId != null;
    }

    /**
     * Returns a copy of this record with {@code position} set to {@code resolvedPosition}, the step that
     * commits a position. The caller ({@code BossFixture.finalizePosition}) rejects a record that already has
     * one; this method just performs the copy.
     */
    public BossRecord finalizedAt(BlockPos resolvedPosition) {
        return new BossRecord(
                bossId,
                kind,
                Objects.requireNonNull(resolvedPosition, "resolvedPosition"),
                bossEntityId,
                alive,
                scale
        );
    }

    /**
     * Returns a copy of this record with {@code bossEntityId} and {@code scale} set: the vanilla mob has
     * spawned at this record's already-finalized {@code position}, at the kind's body size.
     */
    public BossRecord materializedAt(UUID entityId, float scale) {
        return new BossRecord(
                bossId,
                kind,
                position,
                Objects.requireNonNull(entityId, "entityId"),
                alive,
                scale
        );
    }

    /**
     * Returns a copy of this record with {@code bossEntityId} cleared, the inverse of {@link #materializedAt}
     * for a non-player death that sends the boss back to unmaterialized. {@code alive}, {@code position} and
     * {@code scale} are unchanged.
     */
    public BossRecord dematerialized() {
        return new BossRecord(
                bossId,
                kind,
                position,
                null,
                alive,
                scale
        );
    }

    /**
     * Returns a copy of this record with {@code alive} set to {@code false}, the defeat transition. Everything
     * else is unchanged.
     */
    public BossRecord defeated() {
        return new BossRecord(
                bossId,
                kind,
                position,
                bossEntityId,
                false,
                scale
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
        tag.putString("kind", r.kind);
        tag.putBoolean("hasPosition", r.position != null);
        if (r.position != null) {
            tag.putInt("x", r.position.getX());
            tag.putInt("y", r.position.getY());
            tag.putInt("z", r.position.getZ());
        }
        tag.putBoolean("hasEntity", r.bossEntityId != null);
        if (r.bossEntityId != null) {
            tag.putUUID("bossEntityId", r.bossEntityId);
        }
        tag.putBoolean("alive", r.alive);
        tag.putFloat("scale", r.scale);
        return tag;
    }

    static BossRecord load(CompoundTag tag) {
        BlockPos position = tag.getBoolean("hasPosition")
                ? new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"))
                : null;
        UUID entityId = tag.getBoolean("hasEntity") ? tag.getUUID("bossEntityId") : null;
        return new BossRecord(
                tag.getUUID("bossId"),
                tag.getString("kind"),
                position,
                entityId,
                tag.getBoolean("alive"),
                tag.getFloat("scale")
        );
    }
}
