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
        return (LevelJig)
                foundation().requireJigInfo(FrontierKeys.BORDERS_JIG).jig;
    }

    /**
     * FRO_021 investigated whether this bypasses {@code LevelResolver}'s token-aware defer logic
     * in a way that matters: it does bypass it, but its only real call site
     * ({@code BorderCommandHandler.debugCreate}) is server-only, and the server never defers --
     * {@code ServerForgeIngress} binds the world-identity token before introducing any source, so
     * {@code LevelScope}'s UUID is already stable (token-folded) the first time this could
     * possibly run.
     * <p>
     * Since the isReady()/{@code SatchelException.NotReady} redesign, this can throw if called
     * before {@code Satchel.isReady()} -- never actually happens from this method's real
     * (server-only) call site, per the above, but it does happen for the other caller of
     * {@code new LevelScope(...)}-shaped construction: {@link #borders(Level)} below, which
     * checks {@code Satchel.isReady()} proactively before calling this, precisely so it never
     * has to find out the hard way. If a genuinely client-side caller of this method specifically
     * is ever added, it needs the same proactive check -- see {@code RenderContext.getInstance()}
     * for the pattern (it hit the real version of this problem before the redesign: two different
     * UUIDs for the same level, before vs. after the token, corrupting a long-lived cache key).
     */
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
        // isReady() gate: scope(level) constructs a LevelScope directly (not through
        // LevelResolver), and LevelScope now throws SatchelException.NotReady rather than
        // silently falling back if the world-identity token isn't bound yet -- see the
        // isReady()/NotReady redesign this superseded (FRO_021 originally fixed this window with
        // tryScopeInfo alone, before LevelScope's own fallback was removed). This is the one
        // proactive check that keeps this method's whole body safe to run early, same "standby,
        // don't crash" philosophy as the two checks below it.
        if (!Satchel.isReady()) {
            OUT.debug(
                    "[BorderAPI] borders(): Satchel not ready yet → Optional.empty "
                            + "level=" + level.dimension().location()
            );
            return Optional.empty();
        }

        LevelScope scope = scope(level);

        // RM_SAT_019: client-side scope recognition can now be legitimately deferred (waiting on
        // the world-identity token round-trip), so "this jig doesn't know this scope yet" is a
        // real, expected state on top of the "known but not ready" one already handled below --
        // not a hard failure. tryScopeInfo (Satchel-side, added for this) hands that back as an
        // empty Optional instead of requireScopeInfo's throw; treat it exactly like the
        // !isReady() case a few lines down: standby, don't crash.
        var infoOpt = Satchel.require()
                .tryScopeInfo(FrontierKeys.BORDERS_JIG, scope);

        if (infoOpt.isEmpty()) {
            OUT.debug(
                    "[BorderAPI] borders(): scope not yet known → Optional.empty "
                            + "level=" + level.dimension().location()
            );
            return Optional.empty();
        }

        var info = infoOpt.get();

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

        // RM_FRO_011: proposal.insert(border) seeds center/radius from the existing border first
        // -- a null newCenter/newRadius means "leave that seeded value alone," not "pass null
        // through." BorderProposal.radius(int) takes a primitive, so a null Integer auto-unboxes
        // and throws NPE before this method is even entered if passed directly (hit by
        // /border transform <selector> here and <selector> <pos>, both of which omit radius);
        // a null center reaches Border's constructor instead, which requireNonNull()s it (hit by
        // /border transform <selector> radius <r>, which omits position). Only the two forms that
        // supply both center and radius worked before this fix.
        proposal.insert(border);
        if (newCenter != null) {
            proposal.center(newCenter);
        }
        if (newRadius != null) {
            proposal.radius(newRadius);
        }

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
