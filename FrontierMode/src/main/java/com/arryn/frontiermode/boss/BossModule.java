package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.common.navigator.TargetResolverRegistry;
import com.arryn.frontiermode.border.common.navigator.TargetType;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.boss.common.fixture.MaterializeOutcome;
import com.arryn.frontiermode.boss.server.commands.BossCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobInterestRegistry;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.EventHandlers;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.LevelJigConfig;
import com.arryn.satchel.common.newconfig.newnew.MobJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

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

    private BossModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Boss Module Initiating.");
        registerBossJig();
        registerBossMobJig();
        registerNavigatorResolver();
    }

    /** Registers {@code /boss} commands. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossCommands.register(event.getDispatcher());
    }

    /**
     * RM_FRO_029 (Gloria): delegate target for {@code FrontierMode.onMobSpawnFinalize}, mirroring
     * {@link #onRegisterCommands}'s identical shape (a raw Forge {@code @SubscribeEvent} on
     * {@code FrontierMode.java} delegating immediately, by name, to a module static method --
     * {@code FrontierMode.java} stays the single real Forge-subscription point).
     *
     * <p>Never cancels the spawn either way -- Guardian Mobs is a modifier on an already-happening
     * spawn, not a summon. "Standby, don't crash": a level that isn't a real {@link ServerLevel},
     * or a {@link BossGuardiansFixture} that isn't resolvable yet (Satchel not ready, scope not
     * known), is a silent no-op -- the vanilla spawn proceeds unmodified either way.
     */
    public static void onMobSpawnFinalize(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BossAPI.guardians(level).ifPresent(guardians ->
                guardians.onMobSpawnFinalize(event.getEntity(), level));
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
                new JigBundles.FixtureDecl<BossFixture>(
                        FrontierKeys.BOSS,
                        BossFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var bossTellFixture =
                new JigBundles.FixtureDecl<BossTellFixture>(
                        FrontierKeys.BOSS_TELL,
                        BossTellFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        // RM_FRO_029 (Gloria): third BossBundle sibling, event-driven off onMobSpawnFinalize below
        // rather than BOSS_JIG's own tick -- rides this same bundle purely for membership/access
        // parity with its siblings, per guardian-mobs.md's own reasoning.
        var bossGuardiansFixture =
                new JigBundles.FixtureDecl<BossGuardiansFixture>(
                        FrontierKeys.BOSS_GUARDIANS,
                        BossGuardiansFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var bossBundle =
                new JigBundles.BundleDecl<LevelScope, BossBundle>(
                        FrontierKeys.BOSS_BUNDLE,
                        (LevelScope scope) -> new BossBundle(scope, FrontierKeys.BOSS_BUNDLE),
                        List.of(bossFixture, bossTellFixture, bossGuardiansFixture)
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
                        JigPolicies.FlushPolicy.UNLOAD,
                        JigPolicies.MissingDataPolicy.WARN));
        // withExecutionPulse(true) is required alongside withTick(true) -- persistence flush only
        // runs from inside onExecutionPulse.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    // ── BOSS_MOB_JIG ─────────────────────────────────────────────────────────

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

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.MobGainedInterest.class, BossMobJigHandlers::onScopeLoaded)
                        .on(ScopeEvent.MobLostInterest.class, BossMobJigHandlers::onScopeUnloaded)
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
