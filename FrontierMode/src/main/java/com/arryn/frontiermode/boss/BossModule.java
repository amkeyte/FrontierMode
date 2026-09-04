package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.border.common.navigator.TargetResolverRegistry;
import com.arryn.frontiermode.border.common.navigator.TargetType;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.server.commands.BossCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobInterestRegistry;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.EventHandlers;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.LevelJigConfig;
import com.arryn.satchel.common.newconfig.newnew.MobJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import com.arryn.satchel.common.lifecycle.MobDied;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for initializing and hooking the Boss subsystem (RM_FRO_018, "Shirley"). Depends on
 * Border -- must be initialized after {@code BorderModule.init()}, never the reverse; Border has
 * no knowledge Boss exists. See wiki/frontiermode/architecture/boss.md for the design this
 * implements.
 *
 * <p><b>Known limitation, not built here:</b> a boss removed by something that doesn't fire a
 * wired {@code LivingDeathEvent} listener (a bare {@code /kill}, external world-editing, a bug in
 * an unrelated mod) is not detected -- {@code BossFixture} keeps saying {@code alive: true} with
 * the now-gone entity's UUID, and nothing here clears it to trigger a respawn. Deliberately not
 * built: distinguishing that case from an ordinary chunk unload (both look identical to
 * {@code MobJig}'s reason-agnostic teardown) needs either a real death signal
 * ([RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s {@code LivingDeathEvent} listener, out of scope
 * here) or the "expected but absent for N consecutive polls" heuristic boss.md's own "What can
 * actually go wrong" section flags as an open design question, not a settled one. See
 * [FRO_043](../tickets/FRO_043_boss-build.md)'s log for the project owner's own call on this.
 */
public final class BossModule {

    // FRO_081: the old private static final BossRules RULES field (a single DefaultBossRules
    // instance shared across every level) moved to BossRulesFacet -- see that class's own doc.
    // Every call site below now reaches it via fixture.RULES instead.

    // Per-level interest set of currently-materialized boss entity UUIDs -- MobJig's own
    // reconciliation poll resolves these through ForgeEgress every cycle, which is what lets a
    // previously-materialized boss re-attach its BossMobFixture after its chunk unloads and
    // reloads (no live Mob reference survives that gap for MobScope.getFor to re-call). Populated
    // at materialization time below; entries for a since-defeated boss are never removed here --
    // RM_FRO_019 owns defeat detection, and a stale UUID that no longer resolves is a normal,
    // cheap no-op for the poll, not a correctness problem, same precedent SatchelHealth's own
    // never-unwatched INTERESTS map sets.
    private static final Map<Level, Set<UUID>> INTERESTS = new ConcurrentHashMap<>();

    // FRO_062 hotfix, found via real playtest (server console spamming dozens of identical
    // warnings per second): reconcilePathAgainstBossRecords() used to OUT.warn() unconditionally
    // whenever a mismatch existed, with nothing to stop it firing again next tick for the exact
    // same, still-unresolved mismatch. FRO_081: the Map<Level, Set<Integer>> that used to live
    // here moved to BossInfoFacet.lastReportedMismatch -- that facet is already one-per-level
    // (one per BossFixture), so a plain instance field there carries the identical state without
    // the map. See BossInfoFacet.reportMismatch()'s own doc.

    private BossModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Boss Module Initiating.");

        registerBossJig();
        registerBossMobJig();
        registerNavigatorResolver();

    }

    // ─────────────────────────────────────────────
    // RM_FRO_026 ("Dorothy"): TargetType.BOSS resolver registration -- the natural first
    // registered resolver per wiki/frontiermode/architecture/discovery-systems.md
    // #navigation-lives-in-border, "whatever module owns a target type registers its own
    // resolver at init time (BossModule.init() registering a resolver for TargetType.BOSS, say)".
    // Boss already depends on Border (BorderAPI); this is the reverse direction the design
    // expects -- Border/Navigator never import Boss types, Boss registers into Navigator's
    // registry instead.
    // ─────────────────────────────────────────────

    private static void registerNavigatorResolver() {
        // FRO_081: fixture.get() -> fixture.CRUD.get(), a pure pass-through.
        TargetResolverRegistry.register(TargetType.BOSS, (level, bossId) ->
                BossAPI.bosses(level)
                        .flatMap(fixture -> fixture.CRUD.get(bossId))
                        .map(BossRecord::position));
    }

    /**
     * FRO_057 (RM_FRO_022, "Joyce"): registers {@code /boss}, same delegation shape
     * {@code BorderModule.onRegisterCommands} already uses for {@code /border} -- called from
     * {@code FrontierMode.onRegisterCommands} alongside it.
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossCommands.register(event.getDispatcher());
    }

    // ─────────────────────────────────────────────
    // BOSS_JIG -- LevelScope, persisted BossFixture
    // ─────────────────────────────────────────────

    private static void registerBossJig() {
        var bossFixture =
                new JigBundles.FixtureDecl<BossFixture>(
                        FrontierKeys.BOSS,
                        BossFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var bossBundle =
                new JigBundles.BundleDecl<LevelScope, BossBundle>(
                        FrontierKeys.BOSS_BUNDLE,
                        (LevelScope scope) -> new BossBundle(scope, FrontierKeys.BOSS_BUNDLE),
                        List.of(bossFixture)
                );

        JigBundles.Schema<LevelScope> bundles =
                new JigBundles.Schema<>(List.of(bossBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, BossModule::onBossJigTick)
                        // FRO_075: initial Border+Boss bootstrap, moved here from BorderModule so
                        // the reaching-into-the-other-module direction matches "Boss depends on
                        // Border, never the reverse" -- see onBordersScopeLoaded()'s own doc.
                        .on(ScopeEvent.Loaded.class, BossModule::onBordersScopeLoaded)
                        // FRO_086: defeat detection moves off raw Forge onto MobDied (SAT_044).
                        .on(MobDied.class, BossModule::onMobDied)
                        .build();

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.BOSS_JIG);

        // sideApplicability left at LevelJigConfig's own SERVER default -- unlike Border, Boss has
        // no client-rendering need in Tier 1 (no discovery aids), so BOTH isn't warranted here.

        config.bundles().schema(bundles);

        // BossFixture is the sole durable record of boss identity -- needs real persistence, the
        // exact two-call requirement Border's own FRO_014 documents being missed once.
        config.policies().capabilities(
                new JigPolicies.Capabilities(true, false, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.UNLOAD,
                        JigPolicies.MissingDataPolicy.WARN));

        // withExecutionPulse(true) is required alongside withTick(true) -- persistence flush only
        // ever runs from inside onExecutionPulse (see FRO_018's own lesson on Border).
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    /**
     * Guarded by {@code BOSS_JIG}'s own key (checklist item 3). Two logically separate jobs share
     * this tick, per boss.md's "Three questions, three different mechanisms": materializing any
     * unresolved record, and the defensive path/BossFixture reconciliation -- cheap enough to
     * share the cadence, logically independent of each other.
     */
    private static void onBossJigTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BOSS_JIG.equals(info.jigInfo().key)) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        if (!(level instanceof ServerLevel serverLevel)) {
            // BOSS_JIG is SERVER-applicability only; defensive, not expected to trip.
            return;
        }

        var jig = (LevelJig) info.jigInfo().jig;
        BossBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_BUNDLE);
        BossFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS, BossFixture::new);

        finalizeUnpositionedBosses(serverLevel, fixture);
        materializeUnresolvedBosses(serverLevel, fixture);
        reconcilePathAgainstBossRecords(serverLevel, fixture);
    }

    /**
     * RM_FRO_025 ("Donna_02") / FRO_075: orchestrates the level's initial Border-and-Boss
     * bootstrap from {@code BossModule} instead of {@code BorderModule} reaching into Boss --
     * Boss already depends on Border (border.md's "Boss depends on Border, never the reverse"),
     * so this is the direction the architecture actually wants; the old
     * {@code BorderModule.onBordersScopeLoaded} that did {@code BorderAPI.grow()} straight into
     * {@code BossAPI.createBoss(...)} is removed entirely (Architect ruling, FRO_074#1).
     *
     * <p>Registered on {@code BOSS_JIG}'s own {@link EventHandlers} (see {@link #registerBossJig()})
     * even though it filters for {@code BORDERS_JIG}'s key -- {@code ScopeEvent.Loaded} is a single
     * foundation-wide event type, and every handler in this codebase filters by jig key itself
     * rather than relying on which config declared it (see {@link #onBossMobScopeLoaded} and the
     * old {@code BorderModule} version doing the same). {@code BOSS_JIG} is SERVER-applicability
     * only, so unlike that old version (BORDERS_JIG itself is BOTH-applicability) this handler is
     * never compiled/installed on the client at all -- no separate client-side guard needed.
     *
     * <p><b>FRO_081:</b> this method's own {@code BossAPI.createBoss(...)} call below is the
     * sequencing this ticket's own Summary anticipated -- "that call site feeds directly into the
     * new BossCrudFacet, making the new structure immediately useful." The call site here stays
     * unchanged (still {@code BossAPI.createBoss}, not a direct {@code BossCrudFacet.create}
     * call) so this method keeps {@code createBoss}'s own "fixture not available" warning handling
     * rather than duplicating it inline; {@link BossAPI#createBoss} itself now delegates to
     * {@code BossCrudFacet.create} underneath, so the call site's outcome flows through the new
     * facet either way, reached through Boss's sanctioned facade rather than around it.
     */
    private static void onBordersScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BORDERS_JIG.equals(info.jigInfo().key)) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        if (!(level instanceof ServerLevel)) {
            // BOSS_JIG (this handler's own config) is SERVER-applicability only; defensive, not
            // expected to trip -- see this method's own doc comment.
            return;
        }

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        var infoOpt = BorderAPI.INFO(level);
        if (infoOpt.isEmpty()) {
            OUT.warn("[Boss] onBordersScopeLoaded(): BordersFixture not resolvable for overworld"
                    + " level " + level.dimension().location() + " right after its own"
                    + " ScopeEvent.Loaded -- skipping the bootstrap check this cycle.");
            return;
        }

        if (infoOpt.get().seeded()) {
            return;
        }

        // FRO_047: BorderAPI.grow() returns Result instead of a bare Border.
        // FRO_080: BorderAPI.grow(Level) is deprecated in favor of the explicit-center overload,
        // but this call site is the one legitimate exception named in that method's own doc --
        // a level's very first border has no natural center to supply.
        @SuppressWarnings("deprecation")
        Result result = BorderAPI.grow(level);
        if (!result.isSuccess()) {
            OUT.warn("[Boss] onBordersScopeLoaded(): initial grow() failed for overworld level "
                    + level.dimension().location() + ": " + result.message());
            return;
        }

        BossAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
    }

    /**
     * RM_FRO_028 ("Diane"): resolves {@code layer}'s current home {@code Border} by walking the
     * path -- mirrors {@link #reconcilePathAgainstBossRecords}'s own identical loop shape rather
     * than introducing a second way to answer "which border does this layer belong to right
     * now." Empty when Border isn't ready yet, or (a real data bug, same territory
     * {@code reconcilePathAgainstBossRecords} already watches for) no path entry currently
     * carries this layer.
     */
    private static Optional<Border> resolveHomeBorder(ServerLevel level, int layer) {
        Optional<BordersPathFacet> pathOpt = BorderAPI.PATH(level);
        Optional<BordersCrudFacet> crudOpt = BorderAPI.CRUD(level);
        if (pathOpt.isEmpty() || crudOpt.isEmpty()) {
            return Optional.empty();
        }
        BordersPathFacet path = pathOpt.get();
        BordersCrudFacet crud = crudOpt.get();

        for (UUID id : path.all()) {
            Optional<Border> borderOpt = crud.get(id);
            if (borderOpt.isPresent() && borderOpt.get().layer() == layer) {
                return borderOpt;
            }
        }
        return Optional.empty();
    }

    /**
     * RM_FRO_028 ("Diane"): finalizes any record whose position is still null, per
     * border-pregeneration.md's "What this changes in Boss" section -- {@code
     * BorderAPI.isPregenReady} is the single gate; once it passes for a record's home border,
     * {@code BossRulesFacet#choosePosition} (BossRules under the hood) scores candidates against the now-guaranteed-generated
     * disk and the result is committed via {@link BossFixture#finalizePosition}. Runs before
     * {@link #materializeUnresolvedBosses} in the same tick, so a border whose pregeneration
     * finishes this tick doesn't lose a full extra cycle before its boss can materialize.
     */
    private static void finalizeUnpositionedBosses(ServerLevel level, BossFixture fixture) {
        // FRO_081: fixture.unpositioned() -> fixture.INFO.unpositioned(),
        // RULES.choosePosition() -> fixture.RULES.choosePosition(),
        // fixture.finalizePosition() -> fixture.CRUD.finalizePosition() -- same three-facet
        // delegation this whole method's own doc now describes. The loop's control flow (which
        // records to consider, when to skip, what to log) stays here as orchestration.
        for (BossRecord record : fixture.INFO.unpositioned()) {
            Optional<Border> borderOpt = resolveHomeBorder(level, record.layer());
            if (borderOpt.isEmpty()) {
                // Same "real data bug, not a normal transient state" territory
                // reconcilePathAgainstBossRecords already watches for -- nothing new to log here.
                continue;
            }
            Border border = borderOpt.get();

            if (!BorderAPI.isPregenReady(level, border.id())) {
                // Normal wait -- retried next tick once the pregeneration disk finishes.
                continue;
            }

            BlockPos chosen = fixture.RULES.choosePosition(level, border);
            fixture.CRUD.finalizePosition(record.bossId(), chosen);

            // Requested directly after a playtest boss turned out to be sitting at y=0,
            // unreachable, with nothing in the log to say so short of manually teleporting out
            // to check -- this is that missing checkpoint, logged the instant a position commits
            // rather than only once (if ever) the entity actually appears.
            OUT.info("[Boss] finalizeUnpositionedBosses(): boss " + record.bossId() + " (layer "
                    + record.layer() + ") position finalized at " + chosen.getX() + ", "
                    + chosen.getY() + ", " + chosen.getZ() + " -- home border " + border.id()
                    + ". Will materialize once that chunk is loaded.");
        }
    }

    /**
     * "Does an existing record have an actual entity yet?" -- any record with {@code alive: true}
     * and {@code bossEntityId == null}. {@code Level.isLoaded(position)} is a direct boolean
     * query, not a forced load; not loaded is a normal, expected wait, retried next tick against
     * the exact same stored position -- never a new random guess.
     */
    private static void materializeUnresolvedBosses(ServerLevel level, BossFixture fixture) {
        // FRO_081: fixture.unmaterialized() -> fixture.INFO.unmaterialized(),
        // RULES.materialize() -> fixture.RULES.materialize(), fixture.materialize() ->
        // fixture.CRUD.materialize() -- same facet delegation, loop stays here as orchestration.
        for (BossRecord record : fixture.INFO.unmaterialized()) {
            BlockPos xz = record.position();

            if (!level.isLoaded(xz)) {
                continue;
            }

            fixture.RULES.materialize(level, xz, record.layer()).ifPresent(mob -> {
                fixture.CRUD.materialize(record.bossId(), mob.getUUID());
                addInterest(level, mob.getUUID());

                // Immediate-attachment fast path onto MobJig's scope machinery -- the entity is
                // guaranteed loaded at this exact instant, no reason to wait a full poll cycle.
                // The actual BossMobFixture attach happens in onBossMobScopeLoaded below, once the
                // next foundation pulse converges this scope to ready -- getFor only has to
                // introduce the source here, not create the fixture itself.
                if (MobScope.getFor(mob).isEmpty()) {
                    OUT.warn("[Boss] materializeUnresolvedBosses(): MobScope.getFor rejected a mob"
                            + " this method just spawned (bossId=" + record.bossId() + ") -- Satchel"
                            + " not ready, or the entity was already removed. The presence poll"
                            + " (MobInterestRegistry) will still pick it up once ready, since its"
                            + " UUID is already registered as an interest.");
                }
            });
            // Empty result is a normal no-op per BossRules.materialize's own contract (e.g. an
            // all-liquid column) -- retried next tick, same fixed position.
        }
    }

    /**
     * Defensive reconciliation: compares the path's set of {@code Border.layer()} values against
     * {@code BossFixture}'s own set of {@code layer} values. A layer present on the path but
     * missing here is a real data bug (a missed call site, a crash between paired calls, manual
     * world editing) -- logged loudly, never silently self-healed.
     *
     * <p><b>FRO_081:</b> the edge-triggered compare-and-log half of this (FRO_062) moved to
     * {@code BossInfoFacet#reportMismatch}, which is now where the "logged loudly, never silently
     * self-healed" state (last-reported mismatch, one per level) actually lives. This method
     * keeps the cross-module composition -- resolving {@code BorderAPI.PATH}/{@code CRUD} and
     * walking the path to build {@code pathLayers} -- since that reaches into Border, a sibling
     * module, the same orchestration-layer precedent {@link #resolveHomeBorder} already sets;
     * {@code BossInfoFacet} itself never imports {@code BorderAPI}.
     */
    private static void reconcilePathAgainstBossRecords(ServerLevel level, BossFixture fixture) {
        // FRO_047: BorderAPI.borders(Level) is gone -- resolves PATH/CRUD directly instead of a
        // raw BordersFixture reference (this was already the one behavior-bearing migration
        // FRO_047's own done bar calls out by name).
        Optional<BordersPathFacet> pathOpt = BorderAPI.PATH(level);
        Optional<BordersCrudFacet> crudOpt = BorderAPI.CRUD(level);
        if (pathOpt.isEmpty() || crudOpt.isEmpty()) {
            return;
        }
        BordersPathFacet path = pathOpt.get();
        BordersCrudFacet crud = crudOpt.get();

        // FRO_082: layer -> border id, not a bare Set<Integer> -- BossInfoFacet.reportMismatch
        // needs each missing layer's border id to check it against pendingAttach. See that
        // method's own doc.
        Map<Integer, UUID> pathLayers = new HashMap<>();
        for (UUID id : path.all()) {
            crud.get(id).ifPresent(b -> pathLayers.put(b.layer(), b.id()));
        }

        fixture.INFO.reportMismatch(level, pathLayers);
    }

    private static void addInterest(Level level, UUID entityId) {
        INTERESTS.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(entityId);
    }

    /**
     * Forces materialization of {@code bossId}'s record right now, regardless of chunk-loaded
     * state -- FRO_057's {@code /boss mob spawn}, "a real new mechanism: exposes what's
     * currently a private tick-handler concern" per that ticket's own scoping. The tick-driven
     * path in {@link #materializeUnresolvedBosses} only ever proceeds once
     * {@code Level.isLoaded} already reports true; this forces the chunk loaded synchronously
     * first (standard admin-command technique -- safe here since it only ever runs from a
     * player-issued command, never every tick), then runs through the exact same
     * {@code BossRulesFacet#materialize} (BossRules under the hood) call and the same fixture-write/interest-registration steps
     * {@link #materializeUnresolvedBosses} uses -- one materialization codepath, not two.
     *
     * @return true if a new entity was materialized; false if there's no record for
     *         {@code bossId}, it's already materialized, or {@code BossRulesFacet#materialize} (BossRules under the hood) itself
     *         declined (e.g. an all-liquid column -- same normal no-op the tick path tolerates).
     */
    /**
     * Distinguishable outcome for {@link #forceMaterialize} -- FRO_057 playtest turned up that a
     * single collapsed boolean made {@code /boss mob spawn}'s chat feedback impossible to read
     * ("Could not force-spawn" covered three unrelated causes at once). Each case gets its own
     * chat line in {@code BossCommandHandler.mobSpawn} now.
     */
    public enum MaterializeOutcome {
        SPAWNED,
        ALREADY_MATERIALIZED,
        NO_RECORD,
        DECLINED
    }

    public static MaterializeOutcome forceMaterialize(ServerLevel level, UUID bossId) {
        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): BossFixture not available for level "
                    + level.dimension().location() + " -- bossId=" + bossId);
            return MaterializeOutcome.NO_RECORD;
        }
        BossFixture fixture = fixtureOpt.get();

        // FRO_081: fixture.get() -> fixture.CRUD.get(), a pure pass-through.
        Optional<BossRecord> recordOpt = fixture.CRUD.get(bossId);
        if (recordOpt.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): no record for bossId=" + bossId);
            return MaterializeOutcome.NO_RECORD;
        }
        BossRecord record = recordOpt.get();

        if (record.materialized()) {
            // Already has an entity -- a clean no-op, not an error (FRO_057's own call, this
            // build's log).
            return MaterializeOutcome.ALREADY_MATERIALIZED;
        }

        if (!record.positioned()) {
            // Border Pregeneration: nothing to force yet -- this record's home border hasn't
            // finished pregenerating, so BOSS_JIG's own tick hasn't finalized a position for it.
            // Same normal-wait framing finalizeUnpositionedBosses already applies on the tick
            // path; here it's surfaced as DECLINED instead of a silent retry, since this only
            // ever runs from a player-issued command.
            OUT.warn("[Boss] forceMaterialize(): bossId=" + bossId + " has no finalized position"
                    + " yet (home border still pregenerating) -- cannot force-materialize.");
            return MaterializeOutcome.DECLINED;
        }

        BlockPos xz = record.position();
        // Forces the chunk loaded synchronously, unlike Level.isLoaded()'s non-forcing check the
        // tick path relies on -- exactly the "regardless of chunk-loaded state" this command
        // exists to provide. OUT.info (not just .warn) on this path deliberately, temporarily,
        // for the FRO_057 playtest pass -- gives a server-log trail even when chat feedback is
        // the thing being double-checked; fine to drop back to .debug once six-for-six is
        // confirmed.
        OUT.info("[Boss] forceMaterialize(): forcing chunk load at " + xz + " for bossId=" + bossId);
        level.getChunk(xz);

        // FRO_081: RULES.materialize() -> fixture.RULES.materialize().
        Optional<Mob> mobOpt = fixture.RULES.materialize(level, xz, record.layer());
        if (mobOpt.isEmpty()) {
            // Same normal no-op BossRules.materialize()'s own contract documents (e.g. an
            // all-liquid column) -- not an error, but distinguishable from the other three cases.
            OUT.warn("[Boss] forceMaterialize(): BossRules.materialize declined for bossId="
                    + bossId + " at " + xz + " (e.g. an all-liquid column).");
            return MaterializeOutcome.DECLINED;
        }

        Mob mob = mobOpt.get();
        BlockPos resolved = mob.blockPosition();
        // FRO_081: fixture.materialize() -> fixture.CRUD.materialize().
        fixture.CRUD.materialize(record.bossId(), mob.getUUID());
        addInterest(level, mob.getUUID());
        OUT.info("[Boss] forceMaterialize(): spawned bossId=" + bossId
                + " entity=" + mob.getUUID() + " at " + resolved);

        // Immediate-attachment fast path, same as materializeUnresolvedBosses -- the entity is
        // guaranteed loaded at this exact instant.
        if (MobScope.getFor(mob).isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): MobScope.getFor rejected a mob this method just"
                    + " spawned (bossId=" + bossId + ") -- Satchel not ready, or the entity was"
                    + " already removed. The presence poll (MobInterestRegistry) will still pick"
                    + " it up once ready, since its UUID is already registered as an interest.");
        }

        return MaterializeOutcome.SPAWNED;
    }

    // ─────────────────────────────────────────────
    // BOSS_MOB_JIG -- MobScope, not persisted, live BossMobFixture view
    // ─────────────────────────────────────────────

    private static void registerBossMobJig() {
        var bossMobFixture =
                new JigBundles.FixtureDecl<BossMobFixture>(
                        FrontierKeys.BOSS_MOB,
                        BossMobFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var bossMobBundle =
                new JigBundles.BundleDecl<MobScope, BossMobBundle>(
                        FrontierKeys.BOSS_MOB_BUNDLE,
                        (MobScope scope) -> new BossMobBundle(scope, FrontierKeys.BOSS_MOB_BUNDLE),
                        List.of(bossMobFixture)
                );

        JigBundles.Schema<MobScope> bundles =
                new JigBundles.Schema<>(List.of(bossMobBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        // FRO_086: attach/release now fire on mob-kind signals (SAT_044).
                        .on(ScopeEvent.MobGainedInterest.class, BossModule::onBossMobScopeLoaded)
                        .on(ScopeEvent.MobLostInterest.class, BossModule::onBossMobScopeUnloaded)
                        .build();

        MobJigConfig config = new MobJigConfig(FrontierKeys.BOSS_MOB_JIG);

        // Boss's own explicit choice -- defeat-detection is server-only today. MobJigConfig ships
        // no default; stating this explicitly is required, not optional (JigConfigValidator
        // throws if left unset).
        config.binding().sideApplicability(JigPolicies.SideApplicability.SERVER);

        config.bundles().schema(bundles);

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        // A separate call from the MobJigConfig itself -- no extension point exists on
        // JigConfig/Presets/CompiledJigConfig to hang interest on. Supplier is written against
        // Level, not ServerLevel, per RM_SAT_022 ("Roger")'s widened MobInterestSupplier, even
        // though this consumer's own sideApplicability stays SERVER.
        MobInterestRegistry.register(FrontierKeys.BOSS_MOB_JIG, () -> INTERESTS);

        Satchel.registerJigConfig(config);
    }

    /**
     * Attaches {@link BossMobFixture} once {@code MobJig} confirms a tracked boss entity is
     * present -- fires both for a freshly materialized boss (one pulse after
     * {@code MobScope.getFor} introduced it above) and for a previously materialized boss whose
     * chunk just reloaded (reintroduced fresh by the presence poll via {@code MobInterestRegistry},
     * since no live {@code Mob} reference survives an unload/reload gap for {@code getFor} to
     * re-call).
     */
    private static void onBossMobScopeLoaded(ScopeEvent.MobGainedInterest event) {
        if (!event.isJig(FrontierKeys.BOSS_MOB_JIG)) return;
        ScopeInfo info = event.info();
        MobScope scope = (MobScope) info.scope();
        Mob mob = scope.mob();
        UUID entityId = mob.getUUID();

        // FRO_081: fixture.all() -> fixture.CRUD.all().
        Optional<BossRecord> recordOpt = BossAPI.bosses(mob.level())
                .flatMap(fixture -> fixture.CRUD.all().stream()
                        .filter(r -> entityId.equals(r.bossEntityId()))
                        .findFirst());

        if (recordOpt.isEmpty()) {
            OUT.debug("[Boss] onBossMobScopeLoaded: no BossFixture record for entity " + entityId
                    + " -- not a tracked boss, ignoring.");
            return;
        }

        var jig = (MobJig) info.jigInfo().jig;
        BossMobBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_MOB_BUNDLE);
        BossMobFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS_MOB, BossMobFixture::new);
        fixture.attachTo(recordOpt.get().bossId());
    }

    /**
     * Reason-agnostic -- a chunk unload and a genuine removal are indistinguishable here by
     * design (see class docs' "Known limitation"). {@code BossMobFixture} itself is torn down by
     * Satchel's own scope-teardown machinery; this handler exists for visibility, not cleanup.
     */
    private static void onBossMobScopeUnloaded(ScopeEvent.MobLostInterest event) {
        if (!event.isJig(FrontierKeys.BOSS_MOB_JIG)) return;
        ScopeInfo info = event.info();
        MobScope scope = (MobScope) info.scope();
        OUT.debug("[Boss] Boss mob scope unloaded (chunk unload or removal, indistinguishable here): "
                + scope.uuid());
    }

    // ─────────────────────────────────────────────
    // RM_FRO_019 (Karen) -- LivingDeathEvent listener
    // ─────────────────────────────────────────────

    /**
     * Detects a tracked boss's defeat and closes the loop into Border: marks the record
     * defeated, grows a new border centered on the death location, and pairs the next boss
     * record -- see boss.md's "Defeat detection and the border-growth gap". FRO_086:
     * previously wired via {@code MinecraftForge.EVENT_BUS.addListener} on raw
     * {@code LivingDeathEvent}; now registered on {@code BOSS_JIG}'s own {@link EventHandlers}
     * against {@link MobDied} (SAT_044). {@code MobScope.getFor()} is still called
     * synchronously from here -- {@code SatchelEventBus.post()} is synchronous, same call
     * stack, same tick, so the "immediate attachment" guarantee is unchanged.
     */
    private static void onMobDied(MobDied event) {
        // MobDied is only posted server-side (ServerForgeIngress.onLivingDeath guards
        // isClientSide) and only for registered interests -- no instanceof guards needed.
        Mob mob = (Mob) event.forgeEvent().getEntity();
        ServerLevel level = (ServerLevel) event.level();
        OUT.debug("[Boss] onMobDied: received for " + event.uuid());

        Optional<UUID> bossId = resolveBossId(mob);
        if (bossId.isEmpty()) {
            // Most deaths in the world aren't a tracked boss -- normal no-op.
            return;
        }

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] onLivingDeath(): BossFixture not available for level "
                    + level.dimension().location() + " -- can't mark bossId=" + bossId.get()
                    + " defeated.");
            return;
        }
        BossFixture fixture = fixtureOpt.get();

        // Confirmed by a real playtest log, not theoretical: a single physical death can fire
        // more than one LivingDeathEvent (a documented Forge/vanilla quirk -- multiple queued
        // damage instances in the same tick can each drive the entity through death handling).
        // markDefeated()'s own return told us this was already handled; onLivingDeath just never
        // checked it, unlike BossAPI.forceDefeat (FRO_060 guarded that one, and explicitly
        // exempted this method with "a Mob can only die once" -- disproved by tonight's log: a
        // burst of duplicate death events for already-defeated bosses each still ran the full
        // grow()+createBoss() cascade below, minting a wholly extra, unwanted border+boss pair
        // per duplicate and skipping the chain several layers ahead in milliseconds). A redundant
        // death event for an already-defeated boss is now a clean no-op, same as forceDefeat's
        // own guard.
        // FRO_081: fixture.markDefeated() -> fixture.CRUD.markDefeated().
        if (!fixture.CRUD.markDefeated(bossId.get())) {
            return;
        }

        // FRO_083 (boss.md "Cascade gating: on-path, and last one standing"): a hand-placed
        // off-path boss (/boss add, borderId empty) never gates growth -- it's a standalone
        // encounter by construction (FRO_082). An on-path boss (borderId present) only gates
        // growth once it's the LAST living record sharing that borderId -- n:1 boss-to-border
        // cardinality (boss-commands.md) means other siblings on the same layer may still be
        // alive, and defeating just one of them must not advance the path early.
        Optional<UUID> borderId = fixture.CRUD.get(bossId.get()).flatMap(BossRecord::borderId);
        if (borderId.isEmpty()) {
            return;
        }
        if (fixture.INFO.anyAliveWithBorderId(borderId.get())) {
            return;
        }

        BlockPos deathLocation = mob.blockPosition();
        Result result = BorderAPI.grow(level, deathLocation);

        if (!result.isSuccess()) {
            // Border Pregeneration: a boss placed somewhere hazardous (DefaultBossRules
            // .choosePosition's own "all candidates hazardous" last-resort branch) can fall
            // indefinitely and die far outside the level's build height range ("fell out of the
            // world") -- growing straight off that death location then fails this same
            // validation, and until now that failure was final: nothing retried, so the whole
            // chase-the-next-boss chain stalled dead right there, permanently, for the level.
            // Falling back to the boss's own recorded position -- always within build height by
            // construction, since choosePosition() only ever returns a real heightmap-resolved Y
            // -- keeps RM_FRO_019 ("Karen")'s "center on the death location" behavior for every
            // normal kill (this branch is a no-op unless the first grow() already failed) while
            // giving the chain a way to recover from this one specific, previously-unrecoverable
            // failure mode instead of stopping forever.
            // FRO_081: fixture.get() -> fixture.CRUD.get().
            BlockPos fallbackCenter = fixture.CRUD.get(bossId.get()).map(BossRecord::position).orElse(null);
            if (fallbackCenter != null) {
                OUT.warn("[Boss] onLivingDeath(): BorderAPI.grow(level, " + deathLocation
                        + ") failed for defeated bossId=" + bossId.get() + ": " + result.message()
                        + " -- retrying from this boss's own recorded position " + fallbackCenter
                        + " instead of giving up.");
                result = BorderAPI.grow(level, fallbackCenter);
            }
        }

        if (!result.isSuccess()) {
            OUT.warn("[Boss] onLivingDeath(): BorderAPI.grow(level, " + deathLocation
                    + ") failed for defeated bossId=" + bossId.get() + ": " + result.message()
                    + " -- not calling createBoss without a border.");
            return;
        }

        BossAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
    }

    /**
     * Resolves the dying entity's boss id, if it's one. Checks the entity's existing
     * {@link BossMobFixture} first (the no-race common case, via {@code BOSS_MOB_JIG}'s scope);
     * if {@code MobJig} hasn't attached one yet, falls back to a synchronous
     * {@link MobScope#getFor(Mob)} call before concluding it's genuinely not a tracked boss --
     * safe here since the entity is loaded by definition (it just died). Per RM_FRO_019's own
     * ruling and the MobScope.getFor() Contract wiki page.
     *
     * <p><b>Known limitation, logged on FRO_045, built to the ticket's literal wording
     * regardless (project-owner instruction):</b> {@code getFor}'s "immediate attachment"
     * guarantee is about registering the scope, not about populating {@code BossMobFixture} --
     * the actual attach ({@link #onBossMobScopeLoaded}) only runs on a later foundation pulse
     * ({@code ScopeEvent.Loaded}), not synchronously within this same handler call. This
     * fallback therefore does not always close the race within a single death event.
     */
    private static Optional<UUID> resolveBossId(Mob mob) {
        Optional<UUID> existing = existingBossId(mob);
        if (existing.isPresent()) {
            return existing;
        }

        MobScope.getFor(mob);
        return existingBossId(mob);
    }

    private static Optional<UUID> existingBossId(Mob mob) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }

        MobScope scope = new MobScope(mob);
        Optional<ScopeInfo> infoOpt =
                Satchel.require().tryScopeInfo(FrontierKeys.BOSS_MOB_JIG, scope);
        if (infoOpt.isEmpty() || !infoOpt.get().isReady()) {
            return Optional.empty();
        }

        var jig = (MobJig) infoOpt.get().jigInfo().jig;
        BossMobBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_MOB_BUNDLE);
        BossMobFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS_MOB, BossMobFixture::new);

        return Optional.ofNullable(fixture.bossId());
    }
}
