package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.fixture.BordersInfoFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.BordersRulesFacet;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.border.common.player.BorderPlayerBundle;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatus;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusFixture;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
 *   <li>{@link BordersFixture} and its facets</li>
 *   <li>Satchel scope / bundle infrastructure</li>
 * </ul>
 *
 * <p>
 * If Borders are not available for a given level, query methods degrade to {@code Optional.empty()}
 * / an empty list, and mutating methods return a failed {@link Result} -- see each method's own
 * doc. {@link BordersFixture} itself is never handed to another module; the four facet resolvers
 * below ({@link #PATH}, {@link #CRUD}, {@link #RULES}, {@link #INFO}) are the only way anything
 * outside {@code border.common.fixture} reaches Border's state.
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
     * RM_FRO_006: the {@code PlayerJig} analogue of {@link #levelJig()}, backing
     * {@link #playerStatus(ServerPlayer)}/{@link #getRelevant(ServerPlayer)} below.
     */
    public static PlayerJig playerJig() {
        return (PlayerJig)
                foundation().requireJigInfo(FrontierKeys.BORDER_PLAYER_JIG).jig;
    }

    /**
     * FRO_021 investigated whether this bypasses {@code LevelResolver}'s token-aware defer logic
     * in a way that matters: it does bypass it, but its only real call site
     * ({@code BorderCommandHandler.debugCreate}) is server-only, and the server never defers --
     * {@code ServerForgeIngress} binds the world-identity token before introducing any source, so
     * {@code LevelScope}'s UUID is already stable (token-folded) the first time this could
     * possibly run.
     */
    public static LevelScope scope(Level level) {
        return new LevelScope(level);
    }

    /**
     * FRO_047: resolves this level's {@link BordersFixture} -- kept private, replacing the old
     * public {@code borders(Level)}/{@code borders(LevelScope)} surface. {@link #PATH}/
     * {@link #CRUD}/{@link #RULES}/{@link #INFO} below are the only way outside code reaches into
     * it now; the fixture type itself never crosses this method's boundary as a return value.
     * Same "standby, don't crash" body {@code borders(Level)} always had -- isReady() gate, then
     * scope-info known/ready checks, each falling through to {@code Optional.empty()} rather than
     * throwing.
     */
    private static Optional<BordersFixture> resolveFixture(Level level) {
        // isReady() gate: scope(level) constructs a LevelScope directly (not through
        // LevelResolver), and LevelScope now throws SatchelException.NotReady rather than
        // silently falling back if the world-identity token isn't bound yet -- see the
        // isReady()/NotReady redesign this superseded (FRO_021 originally fixed this window with
        // tryScopeInfo alone, before LevelScope's own fallback was removed). This is the one
        // proactive check that keeps this method's whole body safe to run early, same "standby,
        // don't crash" philosophy as the two checks below it.
        if (!Satchel.isReady()) {
            OUT.debug(
                    "[BorderAPI] resolveFixture(): Satchel not ready yet → Optional.empty "
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
                    "[BorderAPI] resolveFixture(): scope not yet known → Optional.empty "
                            + "level=" + level.dimension().location()
            );
            return Optional.empty();
        }

        var info = infoOpt.get();

        if (!info.isReady()) {
            OUT.debug(
                    "[BorderAPI] resolveFixture(): scope NOT ready → Optional.empty "
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
                        "[BorderAPI] resolveFixture(): bundle present but Borders facet ABSENT "
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
    // Facet resolvers -- the only way outside code reaches Border's state
    // ---------------------------------------------------------------------

    public static Optional<BordersPathFacet> PATH(Level level) {
        return resolveFixture(level).map(f -> f.PATH);
    }

    public static Optional<BordersCrudFacet> CRUD(Level level) {
        return resolveFixture(level).map(f -> f.CRUD);
    }

    public static Optional<BordersRulesFacet> RULES(Level level) {
        return resolveFixture(level).map(f -> f.RULES);
    }

    public static Optional<BordersInfoFacet> INFO(Level level) {
        return resolveFixture(level).map(f -> f.INFO);
    }

    // ---------------------------------------------------------------------
    // Queries (safe on both sides)
    // ---------------------------------------------------------------------

    public static Optional<Border> border(Level level, UUID borderId) {
        return CRUD(level).flatMap(c -> c.get(borderId));
    }

    /**
     * FRO_047: throws {@code SatchelException.ScopeNotReady} when the level's Border facet isn't
     * resolvable yet -- same shape this method always had, just resolved via {@link #RULES}
     * instead of the raw fixture. Not-ready is only ever a routine, expected state for callers
     * reachable from a render/tick loop before something else is confirmed ready ({@link
     * BordersFixture}'s own doc, {@code RenderContext} being the real example) -- this method's
     * actual callers ({@code BorderSelector}'s {@code @containing}/{@code @coord} selectors) run
     * from ordinary server-side command dispatch, where the Border facet is expected to already
     * be resolvable by the time a player can even type a command. A not-ready call from there
     * isn't a routine state to quietly route around -- it would mean something else is already
     * wrong, and that should surface loudly rather than be swallowed into an empty list a caller
     * never explicitly asked to distinguish from a real "nothing here."
     */
    public static List<Border> bordersContaining(Level level, BlockPos pos) {
        BordersRulesFacet rules = RULES(level)
                .orElseThrow(() -> new SatchelException.ScopeNotReady(
                        "bordersContaining called before BordersFixture ready "
                                + "level=" + level.dimension().location()
                                + " pos=" + pos
                ));

        return rules.containing(pos);
    }


    /**
     * RM_FRO_006: resolves {@code player}'s live {@link BorderPlayerStatus} snapshot -- the
     * per-player, {@code PlayerJig}-scoped derived-evaluation state ({@code BorderModule.init()}'s
     * {@code onPlayerScopeTick} handler keeps this current every tick). Follows the same
     * "standby, don't crash" discipline as {@link #resolveFixture(Level)}: not ready yet (Satchel
     * not booted, scope not yet known, scope known but not ready) all fall through to
     * {@code Optional.empty()} rather than throwing, since callers like
     * {@code BorderSelector.resolveRelevant} run in ordinary command-dispatch context and a
     * player who hasn't ticked even once yet (e.g. mid-login) is a real, expected transient state,
     * not an error.
     */
    public static Optional<BorderPlayerStatus> playerStatus(ServerPlayer player) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }

        PlayerScope scope = new PlayerScope(player);

        var infoOpt = Satchel.require()
                .tryScopeInfo(FrontierKeys.BORDER_PLAYER_JIG, scope);

        if (infoOpt.isEmpty() || !infoOpt.get().isReady()) {
            return Optional.empty();
        }

        try {
            Optional<BorderPlayerBundle> bundle =
                    Optional.of(playerJig().getOrCreate(scope, FrontierKeys.BORDER_PLAYER_BUNDLE));

            return bundle
                    .flatMap(BorderPlayerBundle::status)
                    .map(BorderPlayerStatusFixture::status);

        } catch (RuntimeException e) {
            throw new SatchelException.AccessFailed(
                    "Failed to resolve BorderPlayerStatus for player "
                            + player.getGameProfile().getName(),
                    e
            );
        }
    }

    /**
     * The border {@code player}'s live {@link BorderPlayerStatus} considers nearest, resolved
     * back to a real {@link Border} in their current level. Backs {@code @relevant} in
     * {@link com.arryn.frontiermode.border.server.commands.BorderSelector}. Empty whenever
     * {@link #playerStatus(ServerPlayer)} is empty (not ready yet), or when it's present but
     * genuinely has no nearest border (empty dimension, or the player hasn't ticked since
     * entering one) -- {@code nearestBorderId} is null in that case per
     * {@code BorderPlayerLogic.evaluate}'s own no-borders branch.
     */
    public static Optional<Border> getRelevant(ServerPlayer player) {
        return playerStatus(player)
                .map(BorderPlayerStatus::nearestBorderId)
                .flatMap(id -> id == null
                        ? Optional.empty()
                        : border(player.serverLevel(), id));
    }

    // ---------------------------------------------------------------------
    // Mutations -- every operation that can fail returns a Result (FRO_047 / FRO_046's ruling)
    // ---------------------------------------------------------------------

    /**
     * Thin wrapper over {@link BordersPathFacet#grow()} -- not-ready degrades to
     * {@link Result#notReady} instead of the old {@code orElseThrow(ScopeNotReady)}.
     */
    public static Result grow(Level level) {
        return PATH(level)
                .map(BordersPathFacet::grow)
                .orElseGet(() -> Result.notReady(
                        "Attempted to grow border but BordersFixture not available "
                                + "level=" + level.dimension().location()
                ));
    }


    public static Result addBorder(
            Level level,
            BlockPos center,
            int radius,
            int layerIndex
    ) {
        Optional<BordersCrudFacet> crudOpt = CRUD(level);
        if (crudOpt.isEmpty()) {
            return Result.notReady(
                    "addBorder called but BordersFixture not available "
                            + "level=" + level.dimension().location()
            );
        }

        BordersCrudFacet crud = crudOpt.get();
        var proposal = crud.getProposal();
        proposal.center(center)
                .radius(radius)
                .layerIndex(layerIndex);

        return crud.applyProposal(proposal);
    }

    public static Result transformBorder(
            Level level,
            UUID borderId,
            BlockPos newCenter,
            Integer newRadius
    ) {
        Optional<BordersCrudFacet> crudOpt = CRUD(level);
        if (crudOpt.isEmpty()) {
            return Result.notReady(
                    "transformBorder called but BordersFixture not available "
                            + "level=" + level.dimension().location()
            );
        }

        BordersCrudFacet crud = crudOpt.get();

        Optional<Border> existing = crud.get(borderId);
        if (existing.isEmpty()) {
            return Result.notFound("No such border: " + borderId);
        }

        var proposal = crud.getProposal();

        // RM_FRO_011: proposal.insert(border) seeds center/radius from the existing border first
        // -- a null newCenter/newRadius means "leave that seeded value alone," not "pass null
        // through."
        proposal.insert(existing.get());
        if (newCenter != null) {
            proposal.center(newCenter);
        }
        if (newRadius != null) {
            proposal.radius(newRadius);
        }

        return crud.applyProposal(proposal);
    }

    /**
     * FRO_047: returns {@link Result} instead of {@code boolean} -- looks the border up first so
     * a successful removal can still hand the caller the {@link Border} that was removed, same
     * "Border on success" shape every other mutating operation here follows.
     */
    public static Result removeBorder(Level level, UUID id) {
        Optional<BordersCrudFacet> crudOpt = CRUD(level);
        if (crudOpt.isEmpty()) {
            return Result.notReady(
                    "removeBorder called but BordersFixture not available "
                            + "level=" + level.dimension().location()
            );
        }

        BordersCrudFacet crud = crudOpt.get();

        Optional<Border> existing = crud.get(id);
        if (existing.isEmpty()) {
            return Result.notFound("No such border: " + id);
        }

        boolean removed = crud.remove(id);
        if (!removed) {
            // Shouldn't happen given the get() above just succeeded, but stay defensive rather
            // than assume -- same discipline the rest of this refactor follows.
            return Result.notFound("No such border: " + id);
        }

        return Result.success(existing.get());
    }
}
