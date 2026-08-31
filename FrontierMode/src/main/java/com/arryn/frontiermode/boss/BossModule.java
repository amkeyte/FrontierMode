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
import com.arryn.frontiermode.boss.server.rules.BossRules;
import com.arryn.frontiermode.boss.server.rules.DefaultBossRules;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

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

    private static final BossRules RULES = new DefaultBossRules();

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
    // warnings per second): reconcilePathAgainstBossRecords() runs on BOSS_JIG's own Tick (every
    // server tick) and used to OUT.warn() unconditionally whenever a mismatch existed, with
    // nothing to stop it firing again next tick for the exact same, still-unresolved mismatch. A
    // world with real missing boss records (e.g. after manual /boss delete cleanup during
    // testing) would warn 20x/second indefinitely. This tracks each level's last-reported
    // mismatch set so the check logs on a real *transition* (new mismatch, changed mismatch, or
    // resolution) rather than every tick the same state persists -- still "logged loudly" per
    // boss.md's own ruling (nothing here is silently self-healed), just not logged on a loop.
    private static final Map<Level, Set<Integer>> LAST_RECONCILIATION_MISMATCH = new ConcurrentHashMap<>();

    private BossModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Boss Module Initiating.");

        registerBossJig();
        registerBossMobJig();
        registerNavigatorResolver();

        // RM_FRO_019 (Karen): defeat detection -> border growth -> next boss record. Plain
        // static listener, not an @SubscribeEvent instance method -- same wiring shape
        // BorderModule.onBlockPlaced uses.
        MinecraftForge.EVENT_BUS.addListener(BossModule::onLivingDeath);
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
        TargetResolverRegistry.register(TargetType.BOSS, (level, bossId) ->
                BossAPI.boss(level)
                        .flatMap(fixture -> fixture.get(bossId))
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

        materializeUnresolvedBosses(serverLevel, fixture);
        reconcilePathAgainstBossRecords(serverLevel, fixture);
    }

    /**
     * "Does an existing record have an actual entity yet?" -- any record with {@code alive: true}
     * and {@code bossEntityId == null}. {@code Level.isLoaded(position)} is a direct boolean
     * query, not a forced load; not loaded is a normal, expected wait, retried next tick against
     * the exact same stored position -- never a new random guess.
     */
    private static void materializeUnresolvedBosses(ServerLevel level, BossFixture fixture) {
        for (BossRecord record : fixture.unmaterialized()) {
            BlockPos xz = record.position();

            if (!level.isLoaded(xz)) {
                continue;
            }

            RULES.materialize(level, xz, record.layer()).ifPresent(mob -> {
                BlockPos resolved = mob.blockPosition();
                fixture.materialize(record.bossId(), resolved, mob.getUUID());
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

        Set<Integer> pathLayers = new HashSet<>();
        for (UUID id : path.all()) {
            crud.get(id).ifPresent(b -> pathLayers.add(b.layer()));
        }

        Set<Integer> missing = new HashSet<>(pathLayers);
        missing.removeAll(fixture.layers());

        // FRO_062: edge-triggered logging -- only speak up when this tick's mismatch set differs
        // from the last one actually reported for this level (see LAST_RECONCILIATION_MISMATCH's
        // own doc above). An unchanged mismatch across ticks is not a new event.
        Set<Integer> previouslyReported = LAST_RECONCILIATION_MISMATCH.getOrDefault(level, Set.of());
        if (missing.equals(previouslyReported)) {
            return;
        }

        if (missing.isEmpty()) {
            OUT.info("[Boss] Reconciliation: previously-mismatched layer(s) " + previouslyReported
                    + " no longer mismatched in level " + level.dimension().location() + ".");
            LAST_RECONCILIATION_MISMATCH.remove(level);
            return;
        }

        OUT.warn("[Boss] Reconciliation: path has border(s) at layer(s) " + missing
                + " with no matching BossFixture record in level " + level.dimension().location()
                + " -- real data bug (missed call site, crash between paired calls, or manual"
                + " world editing), not a normal transient state.");
        LAST_RECONCILIATION_MISMATCH.put(level, Set.copyOf(missing));
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
     * {@link BossRules#materialize} call and the same fixture-write/interest-registration steps
     * {@link #materializeUnresolvedBosses} uses -- one materialization codepath, not two.
     *
     * @return true if a new entity was materialized; false if there's no record for
     *         {@code bossId}, it's already materialized, or {@link BossRules#materialize} itself
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
        Optional<BossFixture> fixtureOpt = BossAPI.boss(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): BossFixture not available for level "
                    + level.dimension().location() + " -- bossId=" + bossId);
            return MaterializeOutcome.NO_RECORD;
        }
        BossFixture fixture = fixtureOpt.get();

        Optional<BossRecord> recordOpt = fixture.get(bossId);
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

        BlockPos xz = record.position();
        // Forces the chunk loaded synchronously, unlike Level.isLoaded()'s non-forcing check the
        // tick path relies on -- exactly the "regardless of chunk-loaded state" this command
        // exists to provide. OUT.info (not just .warn) on this path deliberately, temporarily,
        // for the FRO_057 playtest pass -- gives a server-log trail even when chat feedback is
        // the thing being double-checked; fine to drop back to .debug once six-for-six is
        // confirmed.
        OUT.info("[Boss] forceMaterialize(): forcing chunk load at " + xz + " for bossId=" + bossId);
        level.getChunk(xz);

        Optional<Mob> mobOpt = RULES.materialize(level, xz, record.layer());
        if (mobOpt.isEmpty()) {
            // Same normal no-op BossRules.materialize()'s own contract documents (e.g. an
            // all-liquid column) -- not an error, but distinguishable from the other three cases.
            OUT.warn("[Boss] forceMaterialize(): BossRules.materialize declined for bossId="
                    + bossId + " at " + xz + " (e.g. an all-liquid column).");
            return MaterializeOutcome.DECLINED;
        }

        Mob mob = mobOpt.get();
        BlockPos resolved = mob.blockPosition();
        fixture.materialize(record.bossId(), resolved, mob.getUUID());
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
                        .on(ScopeEvent.Loaded.class, BossModule::onBossMobScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, BossModule::onBossMobScopeUnloaded)
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
    private static void onBossMobScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BOSS_MOB_JIG.equals(info.jigInfo().key)) {
            return;
        }

        MobScope scope = (MobScope) info.scope();
        Mob mob = scope.mob();
        UUID entityId = mob.getUUID();

        Optional<BossRecord> recordOpt = BossAPI.boss(mob.level())
                .flatMap(fixture -> fixture.all().stream()
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
    private static void onBossMobScopeUnloaded(ScopeEvent.Unloaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BOSS_MOB_JIG.equals(info.jigInfo().key)) {
            return;
        }

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
     * record -- see boss.md's "Defeat detection and the border-growth gap". Registered via
     * {@code MinecraftForge.EVENT_BUS.addListener(...)} in {@link #init()}, the same plain
     * static wiring {@code BorderModule.onBlockPlaced} uses -- not an {@code @SubscribeEvent}
     * instance method.
     */
    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (!(mob.level() instanceof ServerLevel level)) {
            // Mutates persisted state -- server-side only, same discipline every other
            // BORDERS_JIG/BOSS_JIG handler in this codebase follows.
            return;
        }

        Optional<UUID> bossId = resolveBossId(mob);
        if (bossId.isEmpty()) {
            // Most deaths in the world aren't a tracked boss -- normal no-op.
            return;
        }

        Optional<BossFixture> fixtureOpt = BossAPI.boss(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] onLivingDeath(): BossFixture not available for level "
                    + level.dimension().location() + " -- can't mark bossId=" + bossId.get()
                    + " defeated.");
            return;
        }
        fixtureOpt.get().markDefeated(bossId.get());

        BlockPos deathLocation = mob.blockPosition();
        Result result = BorderAPI.grow(level, deathLocation);
        if (!result.isSuccess()) {
            OUT.warn("[Boss] onLivingDeath(): BorderAPI.grow(level, " + deathLocation
                    + ") failed for defeated bossId=" + bossId.get() + ": " + result.message()
                    + " -- not calling createBoss without a border.");
            return;
        }

        BossAPI.createBoss(level, result.border());
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
