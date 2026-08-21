package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.server.rules.BorderLogic;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public final class BorderProposal {

    private final BorderLogic logic;

    private boolean consumed = false;

    // ============================================================
    // Identity & UX
    // ============================================================

    private UUID id;
    private String displayName;

    // ============================================================
    // Geometry
    // ============================================================

    private BlockPos center;
    private int radius;

    // ============================================================
    // Topology / progression
    // ============================================================

    private int layerIndex;

    // ============================================================
    // Construction
    // ============================================================

    BorderProposal(
            BorderLogic logic
    ) {
        this.logic = logic;
        applyDefaults();
    }

    // ============================================================
    // Defaults (grouped)
    // ============================================================

    private void applyDefaults() {
        applyIdentityDefaults();
        applyGeometryDefaults();
        applyTopologyDefaults();
    }

    private void applyIdentityDefaults() {
        this.id = UUID.randomUUID();
        this.displayName = logic.getDefaultDisplayName();
    }

    private void applyGeometryDefaults() {
        this.center = logic.defaultCenter();
        this.radius = logic.defaultRadius();
    }

    private void applyTopologyDefaults() {
        this.layerIndex = 0;
    }



    // ============================================================
    // Fluent mutation API
    // ============================================================

    public BorderProposal id(UUID id) {
        requireNotConsumed();
        this.id = id;
        return this;
    }

    public BorderProposal displayName(String name) {
        requireNotConsumed();
        this.displayName = name;
        return this;
    }

    public BorderProposal center(BlockPos center) {
        requireNotConsumed();
        this.center = center;
        return this;
    }

    public BorderProposal radius(int radius) {
        requireNotConsumed();
        this.radius = radius;
        return this;
    }

    public BorderProposal layerIndex(int layerIndex) {
        requireNotConsumed();
        this.layerIndex = layerIndex;
        return this;
    }

    // ============================================================
    // Insert
    // ============================================================

    public BorderProposal insert (Border b){
        requireNotConsumed();

        id(b.id());
        displayName(b.displayName());
        center(b.center());
        radius(b.radius());
        layerIndex(b.layer());
        return this;
    }


// ============================================================
    // Actuation
    // ============================================================



    Border create(BorderAuthority authority) {
        requireNotConsumed();
        consumed = true;

        return new Border(
                authority,
                id,
                displayName,
                center,
                radius,
                layerIndex
        );
    }

    // ============================================================
    // Internal helpers
    // ============================================================

    public void requireNotConsumed() {
        if (consumed) {
            throw new IllegalStateException(
                    "BorderProposal has already been applied"
            );
        }
    }

    // ============================================================
    // Accessors for logic / setting
    // ============================================================

    public UUID id() { return id; }
    public String displayName() { return displayName; }
    public BlockPos center() { return center; }
    public int radius() { return radius; }
    public int layerIndex() { return layerIndex; }
}
