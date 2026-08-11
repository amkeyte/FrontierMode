package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Side-agnostic ingress API for interacting with Borders.
 *
 * <p>
 * This class is intentionally thin:
 * <ul>
 *   <li>No lifecycle logic</li>
 *   <li>No authority checks</li>
 *   <li>No Forge events</li>
 * </ul>
 *
 * <p>
 * All enforcement and validation is delegated to:
 * <ul>
 *   <li>{@link BordersFixture}</li>
 *   <li>Satchel scope / bundle infrastructure</li>
 * </ul>
 *
 * <p>
 * If Borders are not available for a given level, methods will fail loudly.
 */
public final class BorderAPI {

    private BorderAPI() {
    }

    // ---------------------------------------------------------------------
    // Internal resolution helpers
    // ---------------------------------------------------------------------

    private static LogicalFoundation foundation() {
        return Satchel.require();
    }

    public static LevelJig levelJig() {
        try {
            return (LevelJig)
                    foundation().jigInfo(FrontierKeys.BORDERS_JIG).jig;
        } catch (RuntimeException e) {
            throw new SatchelException.JigNotFound(
                    "Borders LevelJig not found for key "
                            + FrontierKeys.BORDERS_JIG,
                    e
            );
        }
    }

    public static LevelScope scope(Level level) {
        return new LevelScope(level);
    }

    public static Optional<Border> border(Level level, UUID borderId) {
        Optional<BordersFixture> opt = borders(level);

        if (opt.isEmpty()) {
            OUT.debug(
                    "[BorderAPI] border(): no BordersFixture "
                            + "level=" + level.dimension().location()
                            + " id=" + borderId
            );
            return Optional.empty();
        }

        return opt.flatMap(b -> b.CRUD.get(borderId));
    }



    public static Optional<BordersFixture> borders(LevelScope scope) {
        return borders(scope.level());
    }

    public static Optional<BordersFixture> borders(Level level) {
        LevelScope scope = scope(level);

        var info = Satchel.require()
                .requireScopeInfo(FrontierKeys.BORDERS_JIG, scope);

        if (!info.isReady()) {
            OUT.debug(
                    "[BorderAPI] borders(): scope NOT ready → Optional.empty "
                            + "level=" + level.dimension().location()
                            + " phase=" + info.phase()
            );
            return Optional.empty();
        }

        try {
            Optional<BordersFixture> result =
                    levelJig()
                            .getOrCreate(scope, FrontierKeys.BORDERS_BUNDLE)
                            .get(FrontierKeys.BORDERS);

            if (result.isEmpty()) {
                OUT.debug(
                        "[BorderAPI] borders(): bundle present but Borders facet ABSENT "
                                + "level=" + level.dimension().location()
                );
            }

            return result;

        } catch (RuntimeException e) {
            throw new SatchelException.AccessFailed(
                    "Failed to resolve BordersFixture for level "
                            + level.dimension().location(),
                    e
            );
        }
    }


    // ---------------------------------------------------------------------
    // Queries (safe on both sides)
    // ---------------------------------------------------------------------

    public static List<Border> bordersContaining(Level level, BlockPos pos) {
        BordersFixture borders = borders(level)
                .orElseThrow(() ->
                        new SatchelException.ScopeNotReady(
                                "bordersContaining called before BordersFixture ready "
                                        + "level=" + level.dimension().location()
                                        + " pos=" + pos
                        )
                );

        return borders.RULES.containing(pos);
    }


    public static Optional<Border> getRelevant(Player player) {
        // Placeholder until player scopes exist
        return Optional.empty();
    }

    // ---------------------------------------------------------------------
    // Mutations (authority enforced by fixture)
    // ---------------------------------------------------------------------

    public static Border grow(Level level) {
        BordersFixture borders = borders(level)
                .orElseThrow(() ->
                        new SatchelException.ScopeNotReady(
                                "Attempted to grow border but BordersFixture not available "
                                        + "level=" + level.dimension().location()
                        )
                );

        return borders.PATH.grow();
    }


    public static Border addBorder(
            Level level,
            BlockPos center,
            int radius,
            int layerIndex
    ) {
        BordersFixture borders = borders(level)
                .orElseThrow(() ->
                        new SatchelException.ScopeNotReady(
                                "addBorder called but BordersFixture not available "
                                        + "level=" + level.dimension().location()
                        )
                );

        var proposal = borders.CRUD.getProposal();
        proposal.center(center)
                .radius(radius)
                .layerIndex(layerIndex);

        borders.CRUD.validateProposal(proposal);
        return borders.CRUD.applyProposal(proposal);
    }

    public static Border transformBorder(
            Level level,
            UUID borderId,
            BlockPos newCenter,
            Integer newRadius
    ) {
        BordersFixture borders = borders(level)
                .orElseThrow(() ->
                        new SatchelException.ScopeNotReady(
                                "transformBorder called but BordersFixture not available "
                                        + "level=" + level.dimension().location()
                        )
                );

        Border border = borders.CRUD.get(borderId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No such border: " + borderId
                        )
                );

        var proposal = borders.CRUD.getProposal();
        proposal.insert(border)
                .center(newCenter)
                .radius(newRadius);

        borders.CRUD.validateProposal(proposal);
        return borders.CRUD.applyProposal(proposal);
    }

    public static boolean removeBorder(Level level, UUID id) {
        BordersFixture borders = borders(level)
                .orElseThrow(() ->
                        new SatchelException.ScopeNotReady(
                                "addBorder called but BordersFixture not available "
                                        + "level=" + level.dimension().location()
                        )
                );


        return borders.CRUD.remove(id);
    }
}
