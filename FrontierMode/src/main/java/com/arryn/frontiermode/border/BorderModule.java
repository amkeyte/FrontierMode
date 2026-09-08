package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.Rendering;
import com.arryn.frontiermode.border.common.FrontierSicknessLogic;
import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.fixture.NavigatorFixture;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.BorderPregenFixture;
import com.arryn.frontiermode.border.common.player.BorderPlayerBundle;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatus;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusFixture;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusProposal;
import com.arryn.frontiermode.border.common.player.ExteriorTellFixture;
import com.arryn.frontiermode.border.server.commands.BorderCommands;
import com.arryn.frontiermode.border.server.rules.PlayerRules;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.EventHandlers;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.LevelJigConfig;
import com.arryn.satchel.common.newconfig.newnew.PlayerJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Objects;

/**
 * Entry point for initializing and hooking the Border subsystem.
 */
public final class BorderModule {
    private BorderModule() {
    }

    // RM_FRO_037 ("Brenda," Frontier Sickness epoch 1): shared RNG for Sick Wildlife's spawn-
    // density roll in onMobSpawnFinalize below -- same static-field, not-persisted shape
    // BossTellFixture.RNG/BossGuardiansFixture.RNG already use.
    private static final RandomSource RNG = RandomSource.create();


    public static void init() {
        OUT.info("FrontierMode - Border Module Initiating.");

        // ─────────────────────────────────────────────
        // Bundle schema -- FRO_021: RM_SAT_012 consolidated ScopeEngine.create()/.get() onto
        // this schema (bundleDecls) directly; the separate BundleFactories.registerFactory(...)
        // call this module used to also make was dead weight (confirmed via grep -- nothing
        // anywhere calls BundleFactories.entryFor(...) anymore) and has been removed, mirroring
        // TrackingModule.init()'s already-cleaned state. See RM_SAT_012's roadmap node for the
        // full history of that duality.
        // ─────────────────────────────────────────────
        var bordersFixture =
                new JigBundles.FixtureDecl<BordersFixture>(
                        FrontierKeys.BORDERS,
                        BordersFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        // RM_FRO_026 ("Dorothy"): NavigatorFixture rides as a sibling fixture in this same
        // bundle, per wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border
        // -- this is the first time this codebase has actually put two fixtures in one bundle
        // (BossBundle/BossMobBundle each still host exactly one), though JigConfigValidator's
        // FixtureDecl -> BundleDecl -> Schema chain already supported it regardless of count.
        var navigatorFixture =
                new JigBundles.FixtureDecl<NavigatorFixture>(
                        FrontierKeys.NAVIGATOR,
                        NavigatorFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        // RM_FRO_027 ("Janet"): BorderCurveFixture, a third sibling fixture -- no tick wiring of
        // its own needed (static descriptors, read on demand), per
        // wiki/frontiermode/architecture/border-curve.md's "Data model" section.
        var curveFixture =
                new JigBundles.FixtureDecl<BorderCurveFixture>(
                        FrontierKeys.CURVE,
                        BorderCurveFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        // RM_FRO_028 ("Diane"): BorderPregenFixture, a fourth sibling fixture -- its own
        // onJigTick() drives its throttled work automatically once this bundle's own JigConfig
        // ticks (already true below), no separate EventHandlers entry needed here.
        var pregenFixture =
                new JigBundles.FixtureDecl<BorderPregenFixture>(
                        FrontierKeys.PREGEN,
                        BorderPregenFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var bordersBundle =
                new JigBundles.BundleDecl<LevelScope, BordersBundle>(
                        FrontierKeys.BORDERS_BUNDLE,
                        (LevelScope scope) -> new BordersBundle(scope, FrontierKeys.BORDERS_BUNDLE),
                        List.of(bordersFixture, navigatorFixture, curveFixture, pregenFixture)
                );

        JigBundles.Schema<LevelScope> bundles =
                new JigBundles.Schema<>(List.of(bordersBundle));

        // ─────────────────────────────────────────────
        // Event handlers (replaces BorderStrap)
        // ─────────────────────────────────────────────
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, Rendering::onClientTick)
                        // RM_FRO_012: RenderContext.CACHE eviction -- see Rendering.onClientUnload
                        // and RenderContext.evict for the full reasoning.
                        .on(ScopeEvent.Unloaded.class, Rendering::onClientUnload)
                        // RM_FRO_018 / FRO_075: level-bootstrap hook moved to
                        // BossModule.onBordersScopeLoaded -- Boss depends on Border, never the
                        // reverse, so Border no longer reaches into Boss for this (Architect
                        // ruling, FRO_074#1).
                        .build();

        // ─────────────────────────────────────────────
        // JigConfig targeting LevelJig -- one BOTH-applicability config. compileForSide()
        // runs once per side's own foundation boot, so this yields one independent
        // LevelJig instance per side automatically, same as the two separate
        // SatchelJigRegistrar.register(...) calls this replaces.
        // ─────────────────────────────────────────────
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.BORDERS_JIG);

        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);

        config.bundles().schema(bundles);

        // Borders persist across restarts -- ScopeEngine_Server's hydrate/flush path
        // requires this capability declared, or it throws AccessFailed the first time
        // a border loads.
        config.policies().capabilities(
                new JigPolicies.Capabilities(true, false, false));

        // capabilities().requiresPersistence=true is cross-checked by JigConfigValidator
        // against this policy's own persistent flag -- declaring the capability alone is
        // not enough; without this call JigConfigValidator.validateCapabilities rejects the
        // config at foundation boot (on both sides, since this config is BOTH-applicability)
        // with "requires persistence but persistence policy is not persistent" before any
        // level ever loads. flushOn/missingPolicy aren't consumed by anything yet (grepped:
        // no reader exists), but set to real values rather than defaults so the policy
        // reads as intentional, not a leftover default.
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.UNLOAD,
                        JigPolicies.MissingDataPolicy.WARN));

        // JigPolicies.Lifecycle.defaults() is (load=true, unload=true, tick=false,
        // executionPulse=false) -- withTick(true) alone leaves executionPulse false. But
        // ScopeEngine_Server.flushIfDirty()/scheduleSync() (persistence flush + client sync) and
        // ScopeEngine_Client.applyIncomingParcels() (parcel drain) are ONLY ever called from
        // onExecutionPulse -- AScopeCoupler.onExecutionPulse() gates on
        // execution().lifecycle().participatesInExecutionPulse() before even calling into the
        // engine. With it false, Border's own flush-then-sync path never ran a single time on
        // either side, regardless of SAT_026's network registration fix -- the send call was
        // simply never reached. withExecutionPulse(true) is required for persistence and
        // client rendering to receive real data at all. See FRO_018.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);

        // ─────────────────────────────────────────────
        // RM_FRO_006 (Sandra): per-player border evaluation, PlayerJig-scoped. A second,
        // independent JigConfig alongside the LevelJig one above -- PlayerTrackingModule (Satchel)
        // is the template this follows for wiring a bundle onto PlayerJig/PlayerScope, same
        // division of labor TrackingModule's LevelJigConfig usage already establishes for this
        // module's own BordersBundle registration above.
        //
        // Deliberately real PlayerJig/PlayerScope, not a LevelScope workaround hosted on
        // BordersBundle -- per-player state (nearest border, distance, inside flag) is genuinely
        // identity-tied and must follow the player across dimensions without a manual handoff.
        // See RM_FRO_006's own roadmap node for the full design ruling (2026-08-14) and RM_SAT_020
        // for why this had to wait on PlayerJig/PlayerScope landing first.
        // ─────────────────────────────────────────────
        var borderPlayerFixture =
                new JigBundles.FixtureDecl<BorderPlayerStatusFixture>(
                        FrontierKeys.BORDER_PLAYER_STATUS,
                        BorderPlayerStatusFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        // RM_FRO_037 ("Brenda"): ExteriorTellFixture, a sibling fixture alongside
        // BorderPlayerStatusFixture -- Sick Wildlife's cosmetic tell. Ticks automatically once
        // this bundle's own PlayerJigConfig ticks (already true below), same "no separate
        // EventHandlers entry needed" shape BorderPregenFixture's own sibling-fixture doc
        // describes for BORDERS_BUNDLE.
        var exteriorTellFixture =
                new JigBundles.FixtureDecl<ExteriorTellFixture>(
                        FrontierKeys.EXTERIOR_TELL,
                        ExteriorTellFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var borderPlayerBundle =
                new JigBundles.BundleDecl<PlayerScope, BorderPlayerBundle>(
                        FrontierKeys.BORDER_PLAYER_BUNDLE,
                        (PlayerScope scope) -> new BorderPlayerBundle(scope, FrontierKeys.BORDER_PLAYER_BUNDLE),
                        List.of(borderPlayerFixture, exteriorTellFixture)
                );

        JigBundles.Schema<PlayerScope> playerBundles =
                new JigBundles.Schema<>(List.of(borderPlayerBundle));

        EventHandlers playerEventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, BorderModule::onPlayerScopeTick)
                        .build();

        // PlayerJigConfig already pins jigType/couplerType/scopeType/sourceType/
        // sideApplicability(SERVER)/scopeResolver/uuidDeterminer to the PlayerJig defaults this
        // module needs, and its lifecycle preset already has withTick(true) -- only bundles and
        // eventHandlers are per-module, same as PlayerTrackingModule's own usage. Not persisted,
        // not networked: BorderPlayerStatus is a live-recomputed snapshot (see
        // BorderPlayerStatusFixture's own doc), so no capabilities()/persistence() override is
        // needed here, unlike the LevelJigConfig above (Border's own state genuinely must survive
        // a restart).
        PlayerJigConfig playerConfig = new PlayerJigConfig(FrontierKeys.BORDER_PLAYER_JIG);

        playerConfig.bundles().schema(playerBundles);

        playerConfig.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(playerEventHandlers);

        Satchel.registerJigConfig(playerConfig);
    }


    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BorderCommands.register(event.getDispatcher());
    }

    // ─────────────────────────────────────────────
    // RM_FRO_006 (Sandra) tick handler
    // ─────────────────────────────────────────────

    /**
     * Recomputes the ticking player's {@link BorderPlayerStatusFixture} against their current
     * level's live border list. Shared-bus caveat, same as every other {@code ScopeEvent} handler
     * in this codebase (e.g. {@code PlayerTrackingModule}'s handlers): {@code ScopeEvent.Tick}
     * fires for every jig scoped to whatever just ticked, not just this one -- the
     * {@code BORDER_PLAYER_JIG} key check below is what keeps this from running against the
     * wrong jig's scopes.
     */
    private static void onPlayerScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BORDER_PLAYER_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = (PlayerJig) info.jigInfo().jig;
        PlayerScope scope = (PlayerScope) info.scope();
        ServerPlayer player = scope.player();

        BorderPlayerBundle bundle = jig.getOrCreate(scope, FrontierKeys.BORDER_PLAYER_BUNDLE);
        BorderPlayerStatusFixture fixture =
                bundle.getOrCreateFixture(FrontierKeys.BORDER_PLAYER_STATUS, BorderPlayerStatusFixture::new);

        // RM_FRO_037 ("Brenda") ROOT CAUSE FOUND: SatchelFixture.onJigTick() is never actually
        // invoked by anything in this codebase -- Satchel's own TrackerFixture proves it
        // (internalTicks stayed 0 across a 2708-tick play session that clearly ticked
        // externally). BorderPlayerStatusFixture was never affected because it was never driven
        // by onJigTick() in the first place -- accept() above is called directly, same manual
        // pattern this line now also uses for ExteriorTellFixture. Filed SAT_049 against
        // Satchel for the underlying dead dispatch path (also silently affects
        // BorderPregenFixture -- zero "[BorderPregen]" log lines exist anywhere across this
        // world's play history despite borders that need pregeneration existing). This call is
        // the FrontierMode-side fix: drive the tick directly, the same way accept() already is,
        // rather than trust a dispatch path that doesn't run.
        ExteriorTellFixture tellFixture =
                bundle.getOrCreateFixture(FrontierKeys.EXTERIOR_TELL, ExteriorTellFixture::new);
        tellFixture.onJigTick();

        List<Border> borders =
                BorderAPI.CRUD(player.serverLevel())
                        .map(BordersCrudFacet::all)
                        .orElseGet(List::of);

        // RM_FRO_037 ("Brenda"): captured before accept() overwrites the fixture's own `current`
        // -- Entry Cue is edge-triggered off the transition this snapshot lets us see, and
        // accept() itself must stay side-effect-free (it's the fixture's own authoritative
        // choke point, not the place to fire a sound).
        BorderPlayerStatus previous = fixture.status();

        BorderPlayerStatus next = fixture.accept(
                new BorderPlayerStatusProposal(player.getUUID()),
                borders,
                player.blockPosition()
        );

        onFrontierSicknessTick(player, previous, next);
    }

    // ─────────────────────────────────────────────
    // RM_FRO_037 ("Brenda," Frontier Sickness epoch 1) -- Entry Cue + the debuff itself. Both
    // read off the same BorderPlayerStatus this tick already computed; neither adds a second
    // pass over the border list.
    // ─────────────────────────────────────────────

    /**
     * Entry Cue (edge-triggered bass-drop tone, targeted to {@code player} only per project
     * owner direction) and Frontier Sickness's climbing debuff (tiered vanilla
     * {@link MobEffectInstance}s, refreshed on a short cadence and otherwise left to run out on
     * their own once no longer refreshed -- see {@link PlayerRules}'s own doc for why that's
     * enough to satisfy the design's "each active effect runs down and clears independently"
     * requirement without this class tracking any decay timers itself).
     */
    private static void onFrontierSicknessTick(ServerPlayer player, BorderPlayerStatus previous, BorderPlayerStatus next) {
        boolean wasOutside = previous != null && previous.frontierDistance() > 0;
        boolean isOutside = next.frontierDistance() > 0;

        if (isOutside && !wasOutside) {
            // RM_FRO_037: real "bass drop" cue, project-owner-supplied asset -- registered as
            // FrontierMode.ENTRY_CUE (see assets/frontiermode/sounds/entry_cue.ogg + sounds.json).
            // Replaces the earlier SoundEvents.AMBIENT_CAVE placeholder.
            // Pitch 1.0 (full speed) -- 0.5 was tuned against the earlier placeholder asset;
            // once the real (and now re-tuned) entry_cue.ogg was wired in, half-speed playback
            // was just distorting a clip that's already tuned the way it's meant to sound.
            EffectsAPI.sendSoundToPlayer(
                    player, FrontierMode.ENTRY_CUE.get(), SoundSource.AMBIENT,
                    player.getX(), player.getY(), player.getZ(), 0.6f, 1.0f);
        }

        applySicknessEffects(player, next.sicknessSeverity());
    }

    private static void applySicknessEffects(ServerPlayer player, double severity) {
        if (severity < PlayerRules.SICKNESS_TIER_1_NAUSEA) {
            // Below every tier -- nothing to (re)apply. Whatever was already granted keeps
            // running down on its own vanilla duration; no explicit removal needed or wanted,
            // per the design's "runs down... after its own short randomized delay," not an
            // instant clear.
            return;
        }

        // Cadence-gated the same way BossRules.tellTickInterval() gates BossTellFixture's own
        // tell pass -- ScopeEvent.Tick fires every tick, but effects only need refreshing every
        // PlayerRules.SICKNESS_EFFECT_REFRESH_INTERVAL_TICKS.
        if (player.tickCount % PlayerRules.SICKNESS_EFFECT_REFRESH_INTERVAL_TICKS != 0) {
            return;
        }

        int duration = PlayerRules.SICKNESS_EFFECT_BASE_DURATION_TICKS
                + RNG.nextInt(PlayerRules.SICKNESS_EFFECT_JITTER_TICKS + 1);

        // Illustrative, non-binding sketch per wiki/frontiermode/design/exterior.md#the-players-affliction
        // -- additive tiers (a higher tier keeps every lower tier's effects too). Amplifier 0
        // throughout; real tuning (including whether severity should scale amplifier, not just
        // which effects apply) is explicitly a playtest question, not decided here.
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0, false, true));

        if (severity >= PlayerRules.SICKNESS_TIER_2_WEAKNESS_FATIGUE) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 0, false, true));
        }
        if (severity >= PlayerRules.SICKNESS_TIER_3_HUNGER_SLOWNESS) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0, false, true));
        }
        if (severity >= PlayerRules.SICKNESS_TIER_4_BLINDNESS_DAMAGE) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0, false, true));
        }
    }

    // ─────────────────────────────────────────────
    // RM_FRO_037 ("Brenda"): Sick Wildlife's density half -- a Border-side MobSpawnEvent
    // .FinalizeSpawn hook, mirroring BossGuardiansFixture's own hook shape but simpler (no
    // player, no boss, no curve -- just "is this passive mob's spawn position in the Exterior").
    // Registered from FrontierMode.onMobSpawnFinalize alongside the existing Boss delegate.
    //
    // Per project-owner direction (FRO_099 planning): NOT a slow continuous climb the way
    // severity above is -- pronounced right at the Frontier edge, flattening to a flat multiplier
    // further out. See FrontierSicknessLogic.wildlifeDensityMultiplier.
    // ─────────────────────────────────────────────

    /**
     * Never cancels the spawn either way -- this is a modifier on an already-happening spawn
     * (spawning an extra nearby companion), not a summon or a block, same discipline
     * {@code BossGuardiansFixture.onMobSpawnFinalize} already follows. "Standby, don't crash":
     * Border state not resolvable yet, or the spawning mob isn't a passive {@link Animal}, is a
     * silent no-op.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        if (!(mob instanceof Animal)) {
            // Density scaling only concerns Sick Wildlife's passive mobs -- everything else
            // (hostiles, the mob that just triggered Guardian Mobs' own handler) is untouched.
            return;
        }

        List<Border> borders = BorderAPI.CRUD(level).map(BordersCrudFacet::all).orElseGet(List::of);
        if (borders.isEmpty()) {
            return;
        }

        BlockPos spawnPos = mob.blockPosition();
        int frontierDistance = borders.stream()
                .mapToInt(b -> BorderAPI.MATH.distanceOutside(spawnPos, b.center(), b.radius()))
                .min()
                .orElse(0);
        if (frontierDistance <= 0) {
            return;
        }

        double multiplier = FrontierSicknessLogic.wildlifeDensityMultiplier(
                frontierDistance, PlayerRules.WILDLIFE_DENSITY_FALLOFF_DISTANCE, PlayerRules.WILDLIFE_DENSITY_MAX_MULTIPLIER);
        double extraSpawnChance = multiplier - 1.0;
        double roll = RNG.nextDouble();

        // TEMP DIAGNOSTIC (2026-09-08, RM_FRO_037): project owner reported no visible density
        // effect -- this line exists only to tell "this hook never even sees an Animal spawn out
        // here" apart from "it sees one but the roll/companion spawn itself is broken." Safe to
        // delete once that's confirmed; OUT.info so it shows up without needing debug logging
        // enabled.
        OUT.info("[Border] onMobSpawnFinalize animal=" + mob.getType()
                + " frontierDistance=" + frontierDistance + " multiplier=" + multiplier
                + " extraSpawnChance=" + extraSpawnChance + " roll=" + roll
                + " willSpawnCompanion=" + (roll < extraSpawnChance));

        if (extraSpawnChance <= 0.0 || roll >= extraSpawnChance) {
            return;
        }

        spawnDensityCompanion(mob, level, spawnPos);
    }

    /**
     * Spawns one extra copy of {@code mob}'s own type near {@code spawnPos} -- an approximation
     * of a true spawn-rate multiplier, since {@code MobSpawnEvent.FinalizeSpawn} fires per
     * already-decided spawn attempt rather than controlling how many attempts vanilla makes.
     * Calls {@link Mob#finalizeSpawn} directly (not through Forge's event bus again) -- the exact
     * same "construct, finalizeSpawn, addFreshEntity" sequence
     * {@code DefaultBossRules.materialize} already uses -- so this never re-enters this class's
     * own {@link #onMobSpawnFinalize} handler for the companion it creates.
     */
    private static void spawnDensityCompanion(Mob mob, ServerLevel level, BlockPos nearPos) {
        Entity created = mob.getType().create(level);
        if (!(created instanceof Mob companion)) {
            return;
        }

        int dx = RNG.nextInt(7) - 3;
        int dz = RNG.nextInt(7) - 3;
        BlockPos offset = nearPos.offset(dx, 0, dz);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, offset);

        companion.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, RNG.nextFloat() * 360.0F, 0.0F);
        companion.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null, null);

        if (!level.addFreshEntity(companion)) {
            OUT.warn("[Border] spawnDensityCompanion(): addFreshEntity failed for " + mob.getType() + " near " + nearPos);
        }
    }
}
