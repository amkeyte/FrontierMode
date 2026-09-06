package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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
        Level scope = ((LevelScope) fixture.scope()).level();
        Level level = fixture.resolveLevel();
        this.center = BorderRules.ACTIVE.chooseInitialCenter(level);
        this.radius = BorderRules.ACTIVE.chooseInitialRadius(level);
    }

    private void applyTopologyDefaults() {
        this.layerIndex = 0;
    }



    // ============================================================
    // Fluent mutation API
    // ============================================================

    /**
     * <b>FRO_059:</b> {@link #insert(Border)} is the one sanctioned way to build a proposal that
     * intentionally collides with an existing border's id (a replace) -- see border.md's
     * "Proposal identity and validation" section. A raw {@code id(existingId)} call outside that
     * path collides on identity alone while every other field keeps this proposal's own fresh
     * defaults (a new random {@code displayName}, a fresh default {@code center}), which is the
     * actually risky shape, not the collision itself. This method stays public and unvalidated by
     * design -- the type is deliberately general-purpose (see "Mutation surface") -- this is a
     * documented contract, not an enforced one.
     */
    public BorderProposal id(UUID id) {
        requireNotConsumed();
        this.id = id;
        return this;
    }

    /**
     * <b>FRO_059:</b> validated at {@link BordersCrudFacet#applyProposal(BorderProposal)} time
     * (blank/whitespace-only rejected, capped at {@link
     * com.arryn.frontiermode.border.common.BorderConstants#MAX_DISPLAY_NAME_LENGTH}) -- not here,
     * since a proposal's fields are meant to be freely mutable up to that point.
     */
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
