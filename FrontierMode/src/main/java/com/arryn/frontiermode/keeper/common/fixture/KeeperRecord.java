package com.arryn.frontiermode.keeper.common.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Immutable Keeper record -- the Keeper's own facts, in {@link KeeperFixture}, under the {@code bossId} of the
 * base {@code BossRecord} it extends (wiki/frontiermode/architecture/keeper.md § The Keeper record).
 *
 * <p>{@code layer} is a plain copy of the originating Border's {@code layer()} taken once at creation, never
 * re-read: the record holds no live reference to any Border. {@code pathPosition} is copied the same way, the
 * Border's 1-based position on the path, and is empty for a Keeper with no path position (hand-placed, or
 * attached to an off-path Border). {@code body} is the body name, chosen once at creation, so a respawned
 * Keeper keeps it. {@code borderId} is empty for a hand-placed off-path Keeper. {@code platformPos} is the
 * base of the bedrock spawn platform, null until it is built.
 */
public final class KeeperRecord {

    private final UUID bossId;
    private final int layer;
    private final OptionalInt pathPosition;
    private final String body;
    private final Optional<UUID> borderId;
    private final BlockPos platformPos; // nullable until the spawn platform is built

    public KeeperRecord(
            UUID bossId,
            int layer,
            OptionalInt pathPosition,
            String body,
            Optional<UUID> borderId,
            BlockPos platformPos
    ) {
        this.bossId = Objects.requireNonNull(bossId, "bossId");
        this.layer = layer;
        this.pathPosition = Objects.requireNonNull(pathPosition, "pathPosition");
        this.body = Objects.requireNonNull(body, "body");
        this.borderId = Objects.requireNonNull(borderId, "borderId");
        this.platformPos = platformPos; // nullable -- see platformBuilt()
    }

    /** The base record this extends. */
    public UUID bossId() {
        return bossId;
    }

    public int layer() {
        return layer;
    }

    /**
     * The home Border's 1-based position on the path, copied once at creation (the Keeper's progression number
     * -- what players are told is its "level"); empty for a Keeper with no path position. Read through
     * {@code KeeperAPI.pathPositionOf}, which falls back to {@link #layer()} when this is empty.
     */
    public OptionalInt pathPosition() {
        return pathPosition;
    }

    /**
     * The Keeper's body, by name from the {@code keeper-bodies} table: the authored body for its path position
     * through 20, a rolled one past it. Fixed at creation.
     */
    public String body() {
        return body;
    }

    /** Empty for a hand-placed off-path Keeper; set once at creation for anything paired with real border-growth. */
    public Optional<UUID> borderId() {
        return borderId;
    }

    /**
     * The base of the bedrock spawn platform (one below the standing position); nullable -- null until the
     * platform is built. Check {@link #platformBuilt()} first.
     */
    public BlockPos platformPos() {
        return platformPos;
    }

    /** True once the spawn platform has been built for this record. Built once, never re-carved. */
    public boolean platformBuilt() {
        return platformPos != null;
    }

    /** Returns a copy of this record with {@code platformPos} set: the platform is in place. */
    public KeeperRecord withPlatform(BlockPos pos) {
        return new KeeperRecord(bossId, layer, pathPosition, body, borderId, Objects.requireNonNull(pos, "pos"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeeperRecord other)) return false;
        return bossId.equals(other.bossId);
    }

    @Override
    public int hashCode() {
        return bossId.hashCode();
    }

    static CompoundTag save(KeeperRecord r) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("bossId", r.bossId);
        tag.putInt("layer", r.layer);
        tag.putBoolean("hasPathPosition", r.pathPosition.isPresent());
        r.pathPosition.ifPresent(p -> tag.putInt("pathPosition", p));
        tag.putString("body", r.body);
        tag.putBoolean("hasBorderId", r.borderId.isPresent());
        r.borderId.ifPresent(id -> tag.putUUID("borderId", id));
        tag.putBoolean("hasPlatform", r.platformPos != null);
        if (r.platformPos != null) {
            tag.putInt("platformX", r.platformPos.getX());
            tag.putInt("platformY", r.platformPos.getY());
            tag.putInt("platformZ", r.platformPos.getZ());
        }
        return tag;
    }

    static KeeperRecord load(CompoundTag tag) {
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
}
