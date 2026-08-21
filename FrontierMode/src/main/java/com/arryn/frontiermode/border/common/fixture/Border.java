package com.arryn.frontiermode.border.common.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable materialized border.
 * <p>
 * Borders are capability-bound to a {@link BorderAuthority} and can only be
 * constructed within this package.
 */
public final class Border {

    // ============================================================
    // Identity & UX
    // ============================================================

    private final UUID id;
    private final String displayName;

    // ============================================================
    // Authority
    // ============================================================

    private final BorderAuthority authority;

    // ============================================================
    // Geometry
    // ============================================================

    private final BlockPos center;
    private final int radius;

    // ============================================================
    // Topology / progression
    // ============================================================

    private final int layerIndex;


    // ============================================================
    // Construction
    // ============================================================

    public Border(
            BorderAuthority authority,
            UUID id,
            String displayName,
            BlockPos center,
            int radius,
            int layerIndex
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = displayName; // nullable is allowed
        this.authority = Objects.requireNonNull(authority, "authority");
        this.center = Objects.requireNonNull(center, "center");
        this.radius = radius;
        this.layerIndex = layerIndex;
    }

    // ============================================================
    // Accessors
    // ============================================================

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public BorderAuthority authority() {
        return authority;
    }

    public BlockPos center() {
        return center;
    }

    public int radius() {
        return radius;
    }

    public int layer() {
        return layerIndex;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Border other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    static CompoundTag save(Border b) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", b.id);
        tag.putInt("x", b.center.getX());
        tag.putInt("y", b.center.getY());
        tag.putInt("z", b.center.getZ());
        tag.putInt("radius", b.radius);
        tag.putInt("layer", b.layerIndex);
        tag.putString("displayName", b.displayName);
        return tag;
    }

    static Border load(BorderAuthority authority, CompoundTag tag) {
        return new Border(
                authority,
                tag.getUUID("id"),
                tag.getString("displayName"),
                new BlockPos(
                        tag.getInt("x"),
                        tag.getInt("y"),
                        tag.getInt("z")
                ),
                tag.getInt("radius"),
                tag.getInt("layer")
        );
    }
}
