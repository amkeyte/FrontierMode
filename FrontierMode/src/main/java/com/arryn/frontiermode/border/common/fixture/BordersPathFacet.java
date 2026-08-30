package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.server.rules.BorderRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * Advance the canonical border progression by one step. Builds and applies the proposal
     * directly -- FRO_047 folded {@code BorderLogic.getInitial()}/{@code grow()}'s mechanics in
     * here rather than delegating to a separate logic class, per the eliminated-{@code BorderLogic}
     * ruling. An empty path takes the bootstrap branch ({@link BorderRules#chooseInitialCenter}/
     * {@link BorderRules#chooseInitialRadius}, layer 0); a non-empty path grows from the tip
     * ({@link BorderRules#chooseNextCenter}/{@link BorderRules#chooseNextRadius}, layer
     * {@code previous.layer() + 1}) -- same branch structure {@code BorderLogic.getInitial()}/
     * {@code grow()} used, just inlined.
     *
     * <p>Returns whatever {@link Result} {@link BordersCrudFacet#applyProposal} produces --
     * on failure, the path is left untouched (no partial append). On success, appends to the path
     * tip and marks this level's path {@code seeded} -- once, inside this same append, covering
     * every caller uniformly (organic growth, an admin command, a future debug trigger). Never
     * cleared once set, even by later removing every border -- see {@code BordersFixture}'s
     * {@code KEY_SEEDED} field doc.
     *
     * <p>See {@link #grow(BlockPos)} for the overload taking an explicit center in place of
     * whichever center this form would otherwise choose.
     */
    public Result grow() {
        fixture.requireServerSide();

        ServerLevel level = fixture.resolveLevel();
        BorderProposal prop = fixture.CRUD.getProposal();

        Optional<Border> tipOpt = tip();
        if (tipOpt.isPresent()) {
            Border previous = tipOpt.get();
            prop.center(BorderRules.ACTIVE.chooseNextCenter(level, previous))
                    .radius(BorderRules.ACTIVE.chooseNextRadius(level, previous))
                    .layerIndex(previous.layer() + 1);
        } else {
            prop.center(BorderRules.ACTIVE.chooseInitialCenter(level))
                    .radius(BorderRules.ACTIVE.chooseInitialRadius(level))
                    .layerIndex(0);
        }

        Result result = fixture.CRUD.applyProposal(prop);
        if (!result.isSuccess()) {
            return result;
        }

        // append to canonical path
        fixture.borderPath.add(result.border().id());
        fixture.markSeeded();
        fixture.markPathDirty();

        return result;
    }

    /**
     * Overload of {@link #grow()} that takes an explicit center instead of deferring to
     * {@link BorderRules#chooseNextCenter}/{@link BorderRules#chooseInitialCenter}. Same
     * two-branch shape as the no-arg form either way -- tip present grows from it (rules-driven
     * radius, {@code previous.layer() + 1}), tip absent bootstraps via the same empty-path
     * branch (rules-driven radius, layer 0) -- only the center source differs. An absent tip is
     * not a special or failure case for this overload: it bootstraps exactly like the no-arg
     * form's own empty-path branch, the same mechanism a level's very first border already needs,
     * not a corruption case unique to a caller-supplied center. See Border's wiki "Mutation
     * surface" section for the full contract (RM_FRO_019 "Karen" is the first consumer, via
     * {@code BorderAPI.grow(Level, BlockPos)}).
     */
    public Result grow(BlockPos center) {
        fixture.requireServerSide();

        ServerLevel level = fixture.resolveLevel();
        BorderProposal prop = fixture.CRUD.getProposal();

        Optional<Border> tipOpt = tip();
        if (tipOpt.isPresent()) {
            Border previous = tipOpt.get();
            prop.center(center)
                    .radius(BorderRules.ACTIVE.chooseNextRadius(level, previous))
                    .layerIndex(previous.layer() + 1);
        } else {
            prop.center(center)
                    .radius(BorderRules.ACTIVE.chooseInitialRadius(level))
                    .layerIndex(0);
        }

        Result result = fixture.CRUD.applyProposal(prop);
        if (!result.isSuccess()) {
            return result;
        }

        // append to canonical path -- same uniform seeding/dirty behavior as the no-arg form
        fixture.borderPath.add(result.border().id());
        fixture.markSeeded();
        fixture.markPathDirty();

        return result;
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
     * RM_FRO_011 found this as a no-op that still reported success ({@code BorderCommandHandler
     * .pathFixLayers} unconditionally told the command sender "Reconciled border layers with path
     * order" even though this method did nothing) -- {@code moveUp}/{@code moveDown} are fully
     * wired, op-exposed commands that reorder the *path list*, but {@link Border#layer()} is
     * immutable (only a fresh {@link BorderProposal} can set it), so reordering the path never
     * touched the layer values {@code DefaultBorderRules.getRelevant()} actually sorts by -- an op
     * could desync path order from difficulty order and the one command whose job is reconciling
     * that silently didn't. That gap is what RM_FRO_015 exists to close; below is the real fix.
     *
     * <p><b>RM_FRO_015, implemented:</b> builds this path's own target layer assignment --
     * {@code target[borderPath.get(i)] = i} for every path member -- and delegates the actual bulk
     * apply to {@link BordersFixture#reassignLayers(Map)}. This still just sets each path member's
     * layer to its path index, no more and no less -- layer collisions with off-path borders aren't
     * a concern to route around anymore (see {@code BordersCrudFacet}'s doc: Layer
     * and Path are definitionally unrelated, and duplicate layers resolve fine via
     * {@code getRelevant()}'s own nearest-center tie-break). Full design: Border Path & Layer
     * Reconciliation (architecture wiki).
     *
     * @return the count of borders whose layer actually changed -- {@code 0} is a real, honest
     * no-op (path and layer order were already consistent), not the previous unconditional
     * {@code false}.
     */
    public int fixLayers() {
        fixture.requireServerSide();

        Map<UUID, Integer> pathTargets = new LinkedHashMap<>();
        List<UUID> path = fixture.borderPath;
        for (int i = 0; i < path.size(); i++) {
            pathTargets.put(path.get(i), i);
        }

        return fixture.reassignLayers(pathTargets);
    }

}
