package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.common.BorderRules;
import com.arryn.satchel.Satchel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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

    /**
     * Advance the canonical border progression by one step. Builds and applies the proposal
     * directly, not through a separate logic class. An empty path takes the bootstrap branch
     * ({@link BorderRules#chooseInitialCenter},
     * layer 1); a non-empty path grows from the tip ({@link BorderRules#chooseNextCenter}, layer
     * {@code previous.layer() + 1}). Either way the radius is
     * {@link BorderRules#radiusForPathPosition} for the new Border's position (the path's size
     * plus one).
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
        Satchel.requireServer();

        Level level = fixture.resolveLevel();
        BorderProposal prop = fixture.CRUD.getProposal();

        Optional<Border> tipOpt = tip();
        if (tipOpt.isPresent()) {
            Border previous = tipOpt.get();
            prop.center(BorderRules.chooseNextCenter(level, previous))
                    .radius(nextRadius(level))
                    .layerIndex(previous.layer() + 1);
        } else {
            prop.center(BorderRules.chooseInitialCenter(level))
                    .radius(nextRadius(level))
                    .layerIndex(1);
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
     * two-branch shape as the no-arg form either way -- tip present grows from it
     * ({@code previous.layer() + 1}), tip absent bootstraps via the same empty-path branch
     * (layer 1), radius from path position in both -- only the center source differs. An absent tip is
     * not a special or failure case for this overload: it bootstraps exactly like the no-arg
     * form's own empty-path branch, the same mechanism a level's very first border already needs,
     * not a corruption case unique to a caller-supplied center. See Border's wiki "Mutation
     * surface" section for the full contract. Reached via {@code BorderAPI.grow(Level, BlockPos)}.
     */
    public Result grow(BlockPos center) {
        Satchel.requireServer();

        Level level = fixture.resolveLevel();
        BorderProposal prop = fixture.CRUD.getProposal();

        Optional<Border> tipOpt = tip();
        if (tipOpt.isPresent()) {
            Border previous = tipOpt.get();
            prop.center(center)
                    .radius(nextRadius(level))
                    .layerIndex(previous.layer() + 1);
        } else {
            prop.center(center)
                    .radius(nextRadius(level))
                    .layerIndex(1);
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

    /** The radius of the Border {@code grow} is about to append: path position is size + 1. */
    private int nextRadius(Level level) {
        return BorderRules.radiusForPathPosition(level, size() + 1);
    }
}
