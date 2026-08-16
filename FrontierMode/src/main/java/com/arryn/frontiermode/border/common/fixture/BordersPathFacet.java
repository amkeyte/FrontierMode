package com.arryn.frontiermode.border.common.fixture;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BordersPathFacet {

    private final BordersFixture fixture;

    BordersPathFacet(BordersFixture fixture) {
        this.fixture = fixture;
    }

    public boolean isEmpty() {
        return fixture.borderPath.isEmpty();
    }

    public int size() {
        return fixture.borderPath.size();
    }

    public List<UUID> all() {
        return List.copyOf(fixture.borderPath);
    }

    public Optional<UUID> tipId() {
        if (fixture.borderPath.isEmpty()) return Optional.empty();
        return Optional.of(fixture.borderPath.get(fixture.borderPath.size() - 1));
    }

    public Optional<Border> tip() {
        return tipId().flatMap(fixture::get);
    }

    public boolean contains(UUID id) {
        return fixture.borderPath.contains(id);
    }

    public boolean contains(Border border) {
        return contains(border.id());
    }

    public int indexOf(UUID id) {
        return fixture.borderPath.indexOf(id); // -1 if absent
    }

    public void insert(int index, Border border) {
        fixture.requireServerSide();

        if (!fixture.all().contains(border)) {
            throw new IllegalArgumentException(
                    "Cannot insert border not owned by this setting: " + border.id()
            );
        }

        if (fixture.borderPath.contains(border.id())) {
            throw new IllegalStateException(
                    "Border already present in path: " + border.id()
            );
        }

        fixture.borderPath.add(index, border.id());
        fixture.markPathDirty();
    }


    public boolean remove(Border border) {
        fixture.requireServerSide();

        boolean removed = fixture.borderPath.remove(border.id());
        if (removed) {
            fixture.markPathDirty();
        }
        return removed;
    }

    /**
     * Advance the canonical border progression by one step.
     * Creates a new border and appends it to the path tip.
     */
    public Border grow() {
        fixture.requireServerSide();

        Border border;

        Optional<Border> tipOpt = tip();
        if (tipOpt.isPresent()) {
            border = fixture.logic.grow(tipOpt.get());
        } else {
            border = fixture.logic.getInitial();
        }

        // append to canonical path
        fixture.borderPath.add(border.id());
        fixture.markPathDirty();

        return border;
    }

    public int insertAll(int index, Collection<Border> borders) {
        fixture.requireServerSide();

        // Validate first (no partial mutation)
        for (Border b : borders) {
            if (!fixture.all().contains(b)) {
                throw new IllegalArgumentException(
                        "Cannot insert border not owned by this setting: " + b.id()
                );
            }
            if (fixture.borderPath.contains(b.id())) {
                throw new IllegalStateException(
                        "Border already present in path: " + b.id()
                );
            }
        }

        int inserted = 0;
        int cursor = index;

        for (Border b : borders) {
            fixture.borderPath.add(cursor, b.id());
            cursor++;
            inserted++;
        }

        if (inserted > 0) {
            fixture.markPathDirty();
        }

        return inserted;
    }


    public int removeAll(Collection<Border> borders) {
        fixture.requireServerSide();

        int removed = 0;
        for (Border b : borders) {
            if (remove(b)) {
                removed++;
            }
        }
        return removed;
    }
// ------------------------------------------------------------
// Ordered path mutation
// ------------------------------------------------------------

    public void moveDown(Border border) {
        fixture.requireServerSide();

        int idx = fixture.borderPath.indexOf(border.id());
        if (idx < 0) {
            throw new IllegalStateException("Border is not present in the path.");
        }
        if (idx == 0) {
            throw new IllegalStateException("Border is already at the start of the path.");
        }

        fixture.borderPath.remove(idx);
        fixture.borderPath.add(idx - 1, border.id());
        fixture.markPathDirty();
    }

    public void moveUp(Border border) {
        fixture.requireServerSide();

        int idx = fixture.borderPath.indexOf(border.id());
        if (idx < 0) {
            throw new IllegalStateException("Border is not present in the path.");
        }
        if (idx == fixture.borderPath.size() - 1) {
            throw new IllegalStateException("Border is already at the end of the path.");
        }

        fixture.borderPath.remove(idx);
        fixture.borderPath.add(idx + 1, border.id());
        fixture.markPathDirty();
    }

    /**
     * RM_FRO_011: was a no-op that still reported success ({@code BorderCommandHandler
     * .pathFixLayers} unconditionally told the command sender "Reconciled border layers with path
     * order" even though this method did nothing) -- {@code moveUp}/{@code moveDown} are fully
     * wired, op-exposed commands that reorder the *path list*, but {@link Border#layerIndex()} is
     * immutable (only a fresh {@link BorderProposal} can set it), so reordering the path never
     * touched the layerIndex values {@code DefaultBorderRules.getRelevant()} actually sorts by --
     * an op could desync path order from difficulty order and the one command whose job is
     * reconciling that silently didn't.
     *
     * <p>
     * Still doesn't reorder layerIndex to match path order -- that needs a real design pass, not
     * a mechanical fix folded into this hardening pass: {@link Border#layerIndex()} can only be
     * changed via a fresh {@link BorderProposal} through {@link BordersCrudFacet#applyProposal},
     * which now (also RM_FRO_011) rejects a layerIndex that collides with any other border's --
     * correct for a single ad-hoc {@code /border add}/{@code /border transform}, but a naive
     * in-place reassignment of every path member's layerIndex to 0..n-1 can transiently collide
     * with an off-path border's existing layerIndex partway through, or with another path member
     * not yet reassigned. That needs either a two-pass reassignment or a temporary validation
     * bypass, neither of which is a change to make blind, without a real build to verify against.
     * Reports honestly instead: no mutation, no false "reconciled" success.
     *
     * @return {@code false} — always, until the reorder logic above is actually implemented.
     */
    public boolean fixLayers() {
        fixture.requireServerSide();

        return false;
    }

}