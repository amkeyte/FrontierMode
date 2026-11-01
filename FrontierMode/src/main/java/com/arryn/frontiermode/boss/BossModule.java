package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.common.navigator.TargetResolverRegistry;
import com.arryn.frontiermode.border.common.navigator.TargetType;
import com.arryn.frontiermode.core.EntityScales;
import com.arryn.frontiermode.boss.common.items.SunWardItem;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.bundle.SentinelPublicBundle;
import com.arryn.frontiermode.boss.common.fixture.SentinelPublicFixture;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.boss.server.commands.BossCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobJigConfig;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.LivingChangeTargetSignal;
import com.arryn.satchel.common.lifecycle.LivingConversionSignal;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

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
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BossIngress.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> buildBossJig());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> buildBossMobJig());
        registerSentinelPublicJig();
        registerNavigatorResolver();
        EntityScales.register(new BossScaleSource());
    }

    /** Registers {@code /boss} commands. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossCommands.register(event.getDispatcher());
    }

    /**
     * Sentinel conversion's entry point: called by {@code SpawnCoordinator}, the one consumer of
     * {@code MobSpawnFinalizing}, in its fixed order after Feral's Slime/MagmaCube deny.
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

    private static LevelJigConfig buildBossJig() {
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
        // RM_FRO_029 (Gloria): third BossBundle sibling. Runtime state only (a Sentinel is a record on
        // the mob); conversion is event-driven off onMobSpawnFinalize, and the fixture rides BOSS_JIG's
        // tick for presence, adds and the defeat kills.
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
                        // Bare, unscoped signals -- declared here only because this is the topically
                        // closest config, not a claim they are BOSS_JIG-scoped.
                        .on(EntityJoinLevelSignal.class, SentinelJigHandlers::onEntityJoinLevel)
                        .on(SpawnPlacementCheckSignal.class, SentinelJigHandlers::onSpawnPlacementCheck)
                        .on(SpawnPositionCheckSignal.class, SentinelJigHandlers::onSpawnPositionCheck)
                        .on(LivingConversionSignal.class, SentinelJigHandlers::onConversion)
                        .build();

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.BOSS_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(bossBundle)));
        // BossFixture is the sole durable record of boss identity -- needs real persistence -- and it is
        // networked: the client receives the fixture, positions included, and runs the same code over it
        // (boss.md § Module wiring). Networking is the second capability flag.
        config.policies().capabilities(new JigPolicies.Capabilities(true, true, false));
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
        // The roster ticks before the mob abilities (Module Graph § Trinket tick order).
        config.trinkets().declare(BossRoster.class, BossRoster::new).order(Order.NORMAL - 10);

        return config;
    }

    // ── BOSS_MOB_JIG ─────────────────────────────────────────────────────────

    /**
     * The mob jig that holds the mob-side abilities, {@code BOTH} like {@code BOSS_JIG} and registered after it: each
     * side's roster pushes that side's interest, and the kind subscribes its abilities once, each on the side it
     * applies to. The jig holds no bundle and no handler on {@code MobGainedInterest} or {@code MobLostInterest}: the
     * mob scope's Trinket attach, {@code Leaving} and detach are the hooks.
     */
    private static MobJigConfig buildBossMobJig() {
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        // Bare, unscoped signals -- declared here only because this is the topically
                        // closest config, not a claim they are BOSS_MOB_JIG-scoped.
                        .on(LivingChangeTargetSignal.class, KeeperCombatHandlers::onChangeTarget)
                        .on(LivingConversionSignal.class, KeeperCombatHandlers::onConversion)
                        .build();

        MobJigConfig config = new MobJigConfig(FrontierKeys.BOSS_MOB_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        // The dynamic host holds the Boss and the kind's abilities as subscribers and forwards Leaving to them;
        // MobAbilities, declared after it, finds the mob's Boss through the roster and subscribes them.
        config.trinkets().declare(DynamicTrinket.class, DynamicTrinket::new);
        config.trinkets().declare(MobAbilities.class, MobAbilities::new);
        return config;
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
}
