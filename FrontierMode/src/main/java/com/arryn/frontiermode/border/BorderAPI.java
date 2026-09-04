package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.BorderPregenFixture;
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
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Internal helper backing {@link #resolveFixture(Level)}/{@link #resolveBordersBundle(Level)}
     * below. FRO_079 removed this method's old public counterpart (it bypassed the
     * {@code isReady()} standby gate for the since-deprecated {@code /border debug create}
     * command, its one external caller) -- demoted to private rather than deleted outright,
     * since {@code resolveFixture}/{@code resolveBordersBundle} both still call it internally
     * (an earlier pass here deleted it entirely by mistake, missing these two unqualified
     * in-class call sites; a dotted-call grep alone doesn't catch those).
     */
    private static LevelJig levelJig() {
        return (LevelJig)
                foundation().requireJigInfo(FrontierKeys.BORDERS_JIG).jig;
    }

    /**
     * RM_FRO_006: the {@code PlayerJig} analogue of {@link #levelJig()} above -- backs
     * {@link #playerStatus(ServerPlayer)}/{@link #getRelevant(ServerPlayer)} below.
     */
    public static PlayerJig playerJig() {
        return (PlayerJig)
                foundation().requireJigInfo(FrontierKeys.BORDER_PLAYER_JIG).jig;
    }

    /**
     * Internal helper backing {@link #resolveFixture(Level)}/{@link #resolveBordersBundle(Level)}
     * below -- constructs a fresh {@link LevelScope} for {@code level}. FRO_079 demoted this
     * method's old public counterpart to private for the same reason as {@link #levelJig()}
     * above -- dead as public surface once {@code BorderCommandHandler.debugCreate} was removed,
     * but still needed internally.
     */
    private static LevelScope scope(Level level) {
        return new LevelScope(level);
    }

    // Edge-triggered logging for resolveFixture()'s three "not ready yet" outcomes below -- same
    // shape BossInfoFacet.lastReportedMismatch (FRO_081; formerly BossModule
    // .LAST_RECONCILIATION_MISMATCH) already uses. RenderContext's own lazy-
    // resolve-and-cache accessors (crud()/path()/info()) call straight back into this method
    // every single render frame for as long as it keeps returning empty, so logging every
    // attempt at DEBUG level was spamming dozens of identical lines per second during the (now
    // survivable, see the Satchel fix on tryScopeInfo) pre-ready window on world join. Tracks
    // each level's last-reported reason so this only logs on a real transition -- a changed
    // reason, or first hitting "not ready" at all.
    private static final Map<Level, String> LAST_NOT_READY_REASON = new ConcurrentHashMap<>();

    private static void logNotReadyOnce(Level level, String reason) {
        String previous = LAST_NOT_READY_REASON.put(level, reason);
        if (!reason.equals(previous)) {
            OUT.debug("[BorderAPI] resolveFixture(): " + reason + " → Optional.empty level="
                    + level.dimension().location());
        }
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
            logNotReadyOnce(level, "Satchel not ready yet");
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
            logNotReadyOnce(level, "scope not yet known");
            return Optional.empty();
        }

        var info = infoOpt.get();

        if (!info.isReady()) {
            logNotReadyOnce(level, "scope NOT ready (phase=" + info.phase() + ")");
            return Optional.empty();
        }

        // Past every "not ready" gate -- clear this level's tracked reason so a future stretch of
        // not-ready (a relog, a reconnect) logs fresh instead of being silently deduped against a
        // stale reason from before this success.
        LAST_NOT_READY_REASON.remove(level);

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

    /**
     * RM_FRO_027 ("Janet") / RM_FRO_028 ("Diane"): resolves this level's {@code BordersBundle}
     * itself, for the sibling fixtures ({@link BorderCurveFixture}, {@code BorderPregenFixture})
     * that aren't part of {@link BordersFixture}'s own four-facet split -- same "standby, don't
     * crash" body as {@link #resolveFixture(Level)}, just stopping one level higher (the bundle,
     * not a specific fixture inside it).
     */
    private static Optional<BordersBundle> resolveBordersBundle(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }

        LevelScope scope = scope(level);

        var infoOpt = Satchel.require().tryScopeInfo(FrontierKeys.BORDERS_JIG, scope);
        if (infoOpt.isEmpty() || !infoOpt.get().isReady()) {
            return Optional.empty();
        }

        try {
            return Optional.of(levelJig().getOrCreate(scope, FrontierKeys.BORDERS_BUNDLE));
        } catch (RuntimeException e) {
            throw new SatchelException.AccessFailed(
                    "Failed to resolve BordersBundle for level " + level.dimension().location(), e);
        }
    }

        /**
     * RM_FRO_028 ("Diane"): kept private, deliberately -- {@link #isPregenReady(Level, UUID)} and
     * {@link #startPregeneration(Level, UUID)} below are the only sanctioned way any cross-module
     * reader (Boss's own tick code) reaches this fixture, per
     * wiki/frontiermode/architecture/border-pregeneration.md's "BorderAPI's new query surface"
     * section -- "Boss's tick code ... calls this rather than reaching into BordersBundle's
     * fixtures directly, the same discipline every other cross-module read in this design
     * already follows."
     */
    private static Optional<BorderPregenFixture> resolvePregenFixture(Level level) {
        return resolveBordersBundle(level).flatMap(BordersBundle::pregen);
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

    /**
     * RM_FRO_027 ("Janet"): unlike {@link #PATH}/{@link #CRUD}/{@link #RULES}/{@link #INFO}
     * above (each a facet carved out of {@link BordersFixture} itself), {@link BorderCurveFixture}
     * is a whole separate sibling fixture in the same bundle -- resolved via
     * {@link #resolveBordersBundle(Level)} rather than {@link #resolveFixture(Level)}.
     */
    public static Optional<BorderCurveFixture> CURVE(Level level) {
        return resolveBordersBundle(level).flatMap(BordersBundle::curve);
    }

    // ---------------------------------------------------------------------
    // Queries (safe on both sides)
    // ---------------------------------------------------------------------

    public static Optional<Border> border(Level level, UUID borderId) {
        return CRUD(level).flatMap(c -> c.get(borderId));
    }

    /**
     * RM_FRO_027 ("Janet"): the reverse lookup per
     * wiki/frontiermode/architecture/border-curve.md#query-surface -- resolves a held
     * {@link BorderCurve} record back to the border geometry (center, radius) it applies to.
     * Implemented here rather than on {@link BorderCurveFixture} itself, composing
     * {@link #border(Level, UUID)} the same way {@link #getRelevant(ServerPlayer)} already
     * composes {@link #playerStatus(ServerPlayer)} with a border lookup -- cross-facet
     * composition lives at this API layer, not inside one fixture reaching into a sibling.
     */
    public static Optional<Border> borderOf(Level level, BorderCurve curve) {
        return border(level, curve.borderId());
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

    /**
     * RM_FRO_028 ("Diane"): {@code false} whenever the fixture itself isn't resolvable yet
     * <em>or</em> {@code borderId}'s own job hasn't completed (or was never started) -- both
     * collapse to the same boolean here, matching {@code BorderPregenFixture.isReadyFor}'s own
     * contract. Boss's position-finalization tick (see boss.md's "Three questions" section)
     * no-ops on this exactly the way it already no-ops on {@code Level.isLoaded()} being false --
     * a normal, expected wait, not an error.
     */
    public static boolean isPregenReady(Level level, UUID borderId) {
        return resolvePregenFixture(level)
                .map(fixture -> fixture.isReadyFor(borderId))
                .orElse(false);
    }

    // ---------------------------------------------------------------------
    // Mutations -- every operation that can fail returns a Result (FRO_047 / FRO_046's ruling)
    // ---------------------------------------------------------------------

    /**
     * Thin wrapper over {@link BordersPathFacet#grow()} -- not-ready degrades to
     * {@link Result#notReady} instead of the old {@code orElseThrow(ScopeNotReady)}.
     *
     * @deprecated FRO_080 (per project owner): standardize on the explicit-center
     * {@link #grow(Level, BlockPos)} overload instead -- this no-center form defers to
     * {@link com.arryn.frontiermode.border.server.rules.BorderRules#chooseNextCenter}/
     * {@code chooseInitialCenter} with no way for a caller to override it. Not removed: 
     * {@code BossModule.onBordersScopeLoaded}'s level-bootstrap listener (FRO_075) is a real,
     * legitimate remaining caller -- a fresh level's very first border genuinely has no natural
     * center to supply, which is exactly the case this overload exists for. New callers should
     * use {@link #grow(Level, BlockPos)}; {@code /border path grow} was migrated to it (FRO_080)
     * and now requires an explicit {@code center} argument, no implicit default.
     */
    @Deprecated
    public static Result grow(Level level) {
        return PATH(level)
                .map(BordersPathFacet::grow)
                .orElseGet(() -> Result.notReady(
                        "Attempted to grow border but BordersFixture not available "
                                + "level=" + level.dimension().location()
                ));
    }

    /**
     * Overload of {@link #grow(Level)} taking an explicit center -- mirrors
     * {@link BordersPathFacet#grow(BlockPos)} one layer up, same not-ready degrade as the no-arg
     * form. RM_FRO_019 ("Karen")'s defeat handler is the first consumer, growing a border
     * centered on the death location.
     */
    public static Result grow(Level level, BlockPos center) {
        return PATH(level)
                .map(path -> path.grow(center))
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

        // RM_FRO_027 ("Janet"): "explicit, not orphaned" -- deleting a Border explicitly deletes
        // its BorderCurve records too, per border-curve.md's "Deletion" section. A missing
        // CURVE fixture (not ready yet) is not itself a reason to fail a border deletion that
        // already succeeded above -- best-effort cleanup, logged rather than escalated.
        CURVE(level).ifPresentOrElse(
                curveFixture -> curveFixture.removeForBorder(id),
                () -> OUT.warn("[Border] removeBorder(): BorderCurveFixture not available "
                        + "level=" + level.dimension().location() + " -- border " + id
                        + " removed, but its curves (if any) were not cleaned up.")
        );

        return Result.success(existing.get());
    }

    /**
     * RM_FRO_028 ("Diane"): starts {@code borderId}'s pregeneration job -- an explicit call,
     * never automatic on border creation, per border-pregeneration.md's own "Starting a border's
     * pregeneration is an explicit call" section. The direct call, paired with whatever code just
     * created the border and its matching boss record -- see that page's own framing, and
     * {@code BossModule.onBordersScopeLoaded} (FRO_075)/{@code BossModule.onLivingDeath}/
     * {@code BossAPI.forceDefeat} for the three call sites that make it a third call alongside
     * those two.
     *
     * <p>Idempotent: a border that already has a job (in progress or complete) still returns
     * {@link Result#success(Border)} -- "ensure this border's pregeneration has been triggered"
     * is what a caller actually wants, and it's already true either way, per
     * {@code BorderPregenFixture.start}'s own "calling this twice is safe" contract.
     */
    public static Result startPregeneration(Level level, UUID borderId) {
        Optional<Border> existing = border(level, borderId);
        if (existing.isEmpty()) {
            return Result.notFound("No such border: " + borderId);
        }

        Optional<BorderPregenFixture> fixtureOpt = resolvePregenFixture(level);
        if (fixtureOpt.isEmpty()) {
            return Result.notReady(
                    "startPregeneration called but BorderPregenFixture not available "
                            + "level=" + level.dimension().location()
            );
        }

        fixtureOpt.get().start(borderId);
        return Result.success(existing.get());
    }

    // ---------------------------------------------------------------------
    // Math -- FRO_078: BorderMath's own operations, routed through BorderAPI's surface so every
    // cross-module touch point into Border -- stateful or not -- goes through the one facade
    // consistently, rather than "state goes through BorderAPI, math doesn't." Namespaced under
    // MATH (BorderAPI.MATH.isInside(...), etc.) rather than flat static methods directly on
    // BorderAPI, mirroring PATH/CRUD/RULES/INFO's own grouped-facet shape above -- Math has no
    // per-level state to resolve (it's stateless geometry), so MATH is a plain nested holder, not
    // a resolver method returning Optional<Facet> the way those four are.
    //
    // BorderMath itself stays public (see its own doc) -- it's used directly by several border.*
    // sub-packages internally (client rendering, fixtures, player logic, rules), and it can't be
    // made package-private to just BorderAPI's own package the way PATH/CRUD/RULES/INFO's
    // backing fixture is, since BorderAPI and BorderMath don't share a package. MATH is the
    // sanctioned path for everything outside border.common -- enforced by doc-comment discipline
    // here, not the compiler, same mitigation BordersFixture's own doc already accepts.
    // ---------------------------------------------------------------------

    public static BorderMath MATH;
//
//    public static final class MATH {
//        private MATH() {
//        }
//
//        public static boolean isInside(Border border, BlockPos pos) {
//            return BorderMath.isInside(border, pos);
//        }
//
//        public static boolean isInside(int range, BlockPos pos, BlockPos center) {
//            return BorderMath.isInside(range, pos, center);
//        }
//
//        public static int distanceToSurface(Border border, BlockPos pos) {
//            return BorderMath.distanceToSurface(border, pos);
//        }
//
//        public static double distanceSqToCenter(Border border, BlockPos pos) {
//            return BorderMath.distanceSqToCenter(border, pos);
//        }
//
//        public static BlockPos randomPointInDisk(RandomSource rng, BlockPos center, int radius) {
//            return BorderMath.randomPointInDisk(rng, center, radius);
//        }
//
//        /**
//         * RM_FRO_018/FRO_072: area-uniform sample in the annulus {@code [innerRadius,
//         * outerRadius]} around {@code center} -- {@code DefaultBossRules.choosePosition}'s own
//         * edge-biased boss placement is the first real cross-module consumer this wrapper was
//         * added for.
//         */
//        public static BlockPos randomPointInAnnulus(RandomSource rng, BlockPos center, int innerRadius, int outerRadius) {
//            return BorderMath.randomPointInAnnulus(rng, center, innerRadius, outerRadius);
//        }
//
//        public static double distanceTo(BlockPos a, BlockPos b) {
//            return BorderMath.distanceTo(a, b);
//        }
//
//        public static Vec3 direction(BlockPos from, BlockPos to) {
//            return BorderMath.direction(from, to);
//        }
//
//        public static double intensityAt(BorderCurve descriptor, double normalizedDistance) {
//            return BorderMath.intensityAt(descriptor, normalizedDistance);
//        }
//
//        public static double intensityAt(Border border, BorderCurve descriptor, BlockPos point) {
//            return BorderMath.intensityAt(border, descriptor, point);
//        }
//    }
}
