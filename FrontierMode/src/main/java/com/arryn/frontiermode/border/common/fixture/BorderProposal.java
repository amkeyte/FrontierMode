package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.server.rules.BorderRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class BorderProposal {

    private final BordersFixture fixture;

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
            BordersFixture fixture
    ) {
        this.fixture = fixture;
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
        this.displayName = fixture.CRUD.getDefaultDisplayName();
    }

    /**
     * FRO_047: calls {@link BorderRules#ACTIVE} directly instead of going through the deleted
     * {@code BorderLogic.defaultCenter()}/{@code defaultRadius()} -- those two just forwarded to
     * {@code rules.chooseInitialCenter(resolveLevel())}/{@code chooseInitialRadius(...)} with no
     * logic of their own.
     */
    private void applyGeometryDefaults() {
        ServerLevel level = fixture.resolveLevel();
        this.center = BorderRules.ACTIVE.chooseInitialCenter(level);
        this.radius = BorderRules.ACTIVE.chooseInitialRadius(level);
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



    Border create() {
        requireNotConsumed();
        consumed = true;

        return new Border(
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
