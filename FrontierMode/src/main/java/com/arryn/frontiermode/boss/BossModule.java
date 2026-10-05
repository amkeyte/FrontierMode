package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.common.navigator.TargetResolverRegistry;
import com.arryn.frontiermode.border.common.navigator.TargetType;
import com.arryn.frontiermode.boss.common.items.SunWardItem;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.common.bundle.BossPublicBundle;
import com.arryn.frontiermode.boss.common.bundle.SentinelPublicBundle;
import com.arryn.frontiermode.boss.common.fixture.SentinelPublicFixture;
import com.arryn.frontiermode.boss.common.fixture.BossPublicFixture;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.boss.common.fixture.MaterializeOutcome;
import com.arryn.frontiermode.boss.server.commands.BossCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobInterestRegistry;
import com.arryn.satchel.common.jig.mob.MobJigConfig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.LivingChangeTargetSignal;
import com.arryn.satchel.common.lifecycle.LivingConversionSignal;
import com.arryn.satchel.common.lifecycle.LivingDamageSignal;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.MobRemoved;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for initializing and hooking the Boss subsystem. Depends on Border -- must be
 * initialized after {@code BorderModule.init()}, never the reverse. See
 * wiki/frontiermode/architecture/boss.md for the design.
 *
 * <p><b>Known limitation:</b> a boss removed without a wired {@code LivingDeathEvent} (bare
 * {@code /kill}, external world editing, unrelated-mod bug) is not detected -- {@link BossFixture}
 * keeps {@code alive: true} and nothing here clears it. Distinguishing that from a chunk unload
 * needs either a real death signal or a "expected but absent for N ticks" heuristic; see
 * FRO_043's log for the design call.
 */
public final class BossModule {

    // The sun ward: registered here, not on BossTellModule.ITEMS (Boss must not depend on BossTell).
    // Still has to be registered onto the mod event bus from FrontierMode's constructor.
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FrontierMode.MODID);

    public static final RegistryObject<Item> SUN_WARD = ITEMS.register("sun_ward", SunWardItem::new);

    private BossModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Boss Module Initiating.");
        registerBossJig();
        registerBossMobJig();
        registerBossPublicJig();
        registerSentinelMobJig();
        registerSentinelPublicJig();
        registerNavigatorResolver();
    }

    /** Registers {@code /boss} commands. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossCommands.register(event.getDispatcher());
    }

    /**
     * Sentinel conversion's entry point: called by {@code SpawnCoordinator}, the one subscriber to
     * {@code MobSpawnFinalizeSignal}, in its fixed order after Feral's Slime/MagmaCube deny.
     *
     * <p>Never cancels the spawn either way -- Sentinels is a modifier on an already-happening
     * spawn, not a summon. "Standby, don't crash": a {@link BossSentinelsFixture} that isn't
     * resolvable yet (Satchel not ready, scope not known) is a silent no-op -- the vanilla spawn
     * proceeds unmodified either way. {@code level} is already confirmed a real {@link ServerLevel}
     * by the caller.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        BossAPI.sentinels(level).ifPresent(sentinels ->
                sentinels.onMobSpawnFinalize(mob, level));
    }

    // ── TargetType.BOSS resolver ──────────────────────────────────────────────

    private static void registerNavigatorResolver() {
        TargetResolverRegistry.register(TargetType.BOSS, (level, bossId) ->
                BossAPI.bosses(level)
                        .flatMap(fixture -> fixture.CRUD.get(bossId))
                        .map(BossRecord::position));
    }

    // ── BOSS_JIG ─────────────────────────────────────────────────────────────

    private static void registerBossJig() {
        var bossFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BOSS,
                        BossFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var bossTellFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BOSS_TELL,
                        BossTellFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        // RM_FRO_029 (Gloria): third BossBundle sibling. Persisted records; conversion is
        // event-driven off onMobSpawnFinalize, and the fixture rides BOSS_JIG's tick for its staging.
        var bossSentinelsFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BOSS_SENTINELS,
                        BossSentinelsFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var bossBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.BOSS_BUNDLE,
                        (LevelScope scope) -> new BossBundle(scope, FrontierKeys.BOSS_BUNDLE),
                        List.of(bossFixture, bossTellFixture, bossSentinelsFixture)
                );

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, BossJigHandlers::onTick)
                        .on(ScopeEvent.Loaded.class, BossJigHandlers::onBordersScopeLoaded)
                        .on(MobDied.class, BossJigHandlers::onMobDied)
                        .build();

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.BOSS_JIG);
        // FRO_094: boss location is deliberately secret from the client -- see boss.md § Module
        // wiring and FRO_093's Architect ruling (discovery-systems.md / border-pregeneration.md's
        // "exploit this closes"). LevelJigConfig already defaults to SERVER, so this is a
        // documentation fix, not a behavior change -- stated explicitly for the real reason,
        // mirroring BOSS_MOB_JIG's own explicit call below ("Defeat detection is server-only").
        config.binding().sideApplicability(JigPolicies.SideApplicability.SERVER);
        config.bundles().schema(new JigBundles.Schema<>(List.of(bossBundle)));
        // BossFixture is the sole durable record of boss identity -- needs real persistence.
        config.policies().capabilities(new JigPolicies.Capabilities(true, false, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));
        // withExecutionPulse(true) is required alongside withTick(true) -- persistence flush only
        // runs from inside onExecutionPulse.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    // ── BOSS_PUBLIC_JIG ──────────────────────────────────────────────────────

    /**
     * FRO_145: the client-syncable boss marker signal, in its own BOTH-applicability jig -- kept
     * separate from {@code BOSS_JIG} so that jig's server-only secrecy policy (boss position) and
     * {@code BossFixture} stay exactly as they are. Written server-side at the
     * {@code BOSS_MOB_JIG} attach/release hooks; read client-side by the eye render layer.
     */
    private static void registerBossPublicJig() {
        var bossPublicFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BOSS_PUBLIC,
                        BossPublicFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var bossPublicBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.BOSS_PUBLIC_BUNDLE,
                        (LevelScope scope) -> new BossPublicBundle(scope, FrontierKeys.BOSS_PUBLIC_BUNDLE),
                        List.of(bossPublicFixture)
                );

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.BOSS_PUBLIC_JIG);
        // Networking requires BOTH (JigConfigValidator).
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);
        config.bundles().schema(new JigBundles.Schema<>(List.of(bossPublicBundle)));
        // Same two-call requirement BORDERS_JIG documents: capability AND matching policy.
        // Networking is what lets the client-side bundle receive a parcel at all (FRO_115).
        config.policies().capabilities(new JigPolicies.Capabilities(true, true, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));
        // withExecutionPulse(true): persistence flush and client sync only ever run from the
        // execution pulse (the FRO_018 lesson). The tick is client-side only, for the boss's
        // close-range flash.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, BossPublicJigHandlers::onTick)
                        .build());

        Satchel.registerJigConfig(config);
    }

    // ── BOSS_MOB_JIG ─────────────────────────────────────────────────────────

    private static void registerBossMobJig() {
        var bossMobFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BOSS_MOB,
                        BossMobFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var bossMobBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.BOSS_MOB_BUNDLE,
                        (MobScope scope) -> new BossMobBundle(scope, FrontierKeys.BOSS_MOB_BUNDLE),
                        List.of(bossMobFixture)
                );

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.MobGainedInterest.class, BossMobJigHandlers::onScopeLoaded)
                        .on(ScopeEvent.MobLostInterest.class, BossMobJigHandlers::onScopeUnloaded)
                        // Bare, unscoped signals -- declared here only because this is the topically
                        // closest config, not a claim they are BOSS_MOB_JIG-scoped.
                        .on(LivingDamageSignal.class, KeeperCombatHandlers::onLivingDamage)
                        .on(LivingChangeTargetSignal.class, KeeperCombatHandlers::onChangeTarget)
                        .on(LivingConversionSignal.class, KeeperCombatHandlers::onConversion)
                        .build();

        MobJigConfig config = new MobJigConfig(FrontierKeys.BOSS_MOB_JIG);
        // Defeat detection is server-only. MobJigConfig has no default; must be stated explicitly.
        config.binding().sideApplicability(JigPolicies.SideApplicability.SERVER);
        config.bundles().schema(new JigBundles.Schema<>(List.of(bossMobBundle)));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        MobInterestRegistry.register(FrontierKeys.BOSS_MOB_JIG, () -> BossInterests.MAP);
        Satchel.registerJigConfig(config);
    }

    // ── SENTINEL_MOB_JIG ─────────────────────────────────────────────────────

    /**
     * Tracks the sentinel entities {@code BossSentinelsFixture}'s records name. Server-only, and an
     * empty bundle schema: there is no per-mob state to hold. The interest set is the fixture's live
     * record-key view, registered per level from {@code BOSS_JIG}'s tick.
     */
    private static void registerSentinelMobJig() {
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.MobGainedInterest.class, SentinelJigHandlers::onMobGainedInterest)
                        .on(ScopeEvent.MobLostInterest.class, SentinelJigHandlers::onMobLostInterest)
                        // Bare, unscoped signals -- declared here only because this is the topically
                        // closest config, not a claim they are SENTINEL_MOB_JIG-scoped.
                        .on(EntityJoinLevelSignal.class, SentinelJigHandlers::onEntityJoinLevel)
                        .on(SpawnPlacementCheckSignal.class, SentinelJigHandlers::onSpawnPlacementCheck)
                        .on(SpawnPositionCheckSignal.class, SentinelJigHandlers::onSpawnPositionCheck)
                        .on(MobDied.class, SentinelJigHandlers::onMobDied)
                        .on(MobRemoved.class, SentinelJigHandlers::onMobRemoved)
                        .build();

        MobJigConfig config = new MobJigConfig(FrontierKeys.SENTINEL_MOB_JIG);
        config.binding().sideApplicability(JigPolicies.SideApplicability.SERVER);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        MobInterestRegistry.register(FrontierKeys.SENTINEL_MOB_JIG, () -> SentinelInterests.MAP);
        Satchel.registerJigConfig(config);
    }

    // ── SENTINEL_PUBLIC_JIG ──────────────────────────────────────────────────

    /**
     * The client-visible sentinel record, in its own BOTH-applicability jig. Networked, not
     * persisted; the fixture's own tick flushes its coalesced push.
     */
    private static void registerSentinelPublicJig() {
        var sentinelPublicFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.SENTINEL_PUBLIC,
                        SentinelPublicFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var sentinelPublicBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.SENTINEL_PUBLIC_BUNDLE,
                        (LevelScope scope) -> new SentinelPublicBundle(scope, FrontierKeys.SENTINEL_PUBLIC_BUNDLE),
                        List.of(sentinelPublicFixture)
                );

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.SENTINEL_PUBLIC_JIG);
        // Networking requires BOTH (JigConfigValidator).
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);
        config.bundles().schema(new JigBundles.Schema<>(List.of(sentinelPublicBundle)));
        config.policies().capabilities(new JigPolicies.Capabilities(false, true, false));
        // The execution pulse drives client sync; the tick runs the fixture's coalesced push flush
        // and, client side, the trail.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, SentinelJigHandlers::onSentinelPublicTick)
                        .build());

        Satchel.registerJigConfig(config);
    }

    // ── command surface ───────────────────────────────────────────────────────

    /**
     * Forces materialization of {@code bossId} regardless of chunk-loaded state --
     * used by {@code /boss mob spawn}. Delegates to {@link com.arryn.frontiermode.boss.common.fixture.BossSpawnFacet#forceMaterialize}.
     */
    public static MaterializeOutcome forceMaterialize(Level level, UUID bossId) {
        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): BossFixture not available for level "
                    + level.dimension().location() + " -- bossId=" + bossId);
            return MaterializeOutcome.NO_RECORD;
        }
        return fixtureOpt.get().SPAWN.forceMaterialize(level, bossId, uuid -> BossInterests.add(level, uuid));
    }
}
