package com.arryn.frontiermode.border.common.fixture;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable materialized border.
 * <p>
 * Borders can only be constructed within this package: {@link BorderProposal#create()} builds a new one, and
 * {@code BordersFixture} builds one for each it loads.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class Border {

    // ============================================================
    // Identity & UX
    // ============================================================

    private final UUID id;
    private final String displayName;

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
            UUID id,
            String displayName,
            BlockPos center,
            int radius,
            int layerIndex
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = displayName; // nullable is allowed
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
}
