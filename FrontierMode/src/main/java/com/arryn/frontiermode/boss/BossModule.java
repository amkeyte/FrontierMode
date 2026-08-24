package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
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

    private BossModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Boss Module Initiating.");

        registerBossJig();
        registerBossMobJig();
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
        Optional<BordersFixture> bordersOpt = BorderAPI.borders(level);
        if (bordersOpt.isEmpty()) {
            return;
        }
        BordersFixture borders = bordersOpt.get();

        Set<Integer> pathLayers = new HashSet<>();
        for (UUID id : borders.PATH.all()) {
            borders.CRUD.get(id).ifPresent(b -> pathLayers.add(b.layer()));
        }

        Set<Integer> missing = new HashSet<>(pathLayers);
        missing.removeAll(fixture.layers());

        if (!missing.isEmpty()) {
            OUT.warn("[Boss] Reconciliation: path has border(s) at layer(s) " + missing
                    + " with no matching BossFixture record in level " + level.dimension().location()
                    + " -- real data bug (missed call site, crash between paired calls, or manual"
                    + " world editing), not a normal transient state.");
        }
    }

    private static void addInterest(Level level, UUID entityId) {
        INTERESTS.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(entityId);
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
}
