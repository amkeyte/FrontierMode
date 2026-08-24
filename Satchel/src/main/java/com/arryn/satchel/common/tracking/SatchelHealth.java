package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobInterestRegistry;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.EventHandlers;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.TrackerFixture;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.LevelJigConfig;
import com.arryn.satchel.common.newconfig.newnew.MobJigConfig;
import com.arryn.satchel.common.newconfig.newnew.PlayerJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.LogicalSide;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SAT_039/SAT_040 -- the permanent home for every run-monitoring / self-verification module
 * Satchel owns, not a single-purpose test. Absorbs {@code MobTrackingModule}, {@code
 * TrackingModule} (LevelJig), and {@code PlayerTrackingModule} (PlayerJig) wholesale (same
 * registered bundle/fixture/jig key IDs each already used, same public surfaces -- this is a
 * relocation, not a second copy) and adds each jig kind its own violation check: an automatic,
 * silent-on-pass, loud-on-fail regression backstop proving that kind's teardown only ever happens
 * for a reason its own contract actually allows. SAT_039 built {@code MobJig}'s (a CLIENT/BOTH
 * gap nothing in either repo had ever exercised, see SAT_039's audit and RM_SAT_022's log);
 * SAT_040 (this pass) adds {@code LevelJig} and {@code PlayerJig}'s.
 *
 * <p><b>MobJig, LevelJig, and PlayerJig are not the same shape underneath, and their checks
 * aren't copy-pasted from one another.</b> MobJig's teardown is <i>inferred</i> -- a poll-driven
 * reconcile cycle (~every 20 ticks) decides a scope is gone based on whether the backing entity
 * can still be resolved, which is exactly the kind of decision that can be wrong (RM_SAT_022's
 * whole premise). LevelJig and PlayerJig's teardown is <i>authoritative</i> -- {@code
 * ServerForgeIngress}/{@code ClientForgeIngress} call {@code tryRemoveSource} from exactly one
 * real Forge event each ({@code LevelEvent.Unload}, {@code PlayerLoggedOutEvent}), never inferred.
 * So their violation checks aren't "is the backing thing still there" (Mob's shape) but "did
 * teardown get triggered while the backing thing is still demonstrably live" -- catching a future
 * regression that calls {@code tryRemoveSource} from the wrong place (see
 * {@link #onLevelScopeUnloaded} and {@link #onPlayerScopeUnloaded} for each kind's own reasoning).
 *
 * <p><b>Shape: a live-run monitor, not a build-time (JUnit/GameTest) test.</b> Project owner's
 * explicit call: what's wanted is a permanent, in-repo "SatchelHealth" module that proves a
 * major service each jig kind provides hasn't broken, checked every time someone actually runs a
 * client/server, not gated behind {@code gradle test}. That trades CI-style gating (nothing stops
 * a broken commit from landing unnoticed if nobody runs the client that session) for reaching
 * something JUnit-without-Forge fundamentally can't: a live entity resolution path. See this
 * class's own log entry on SAT_039 for the full reasoning and that trade-off spelled out.
 *
 * <p><b>The canary.</b> {@code MobTrackingModule}'s old {@code SERVER}-only {@code MobJigConfig}
 * becomes {@code BOTH} here -- the actual fix under test, and RM_SAT_022 ("Roger") is what makes
 * it correct. Until Roger lands, a client-scoped canary WILL be torn down every reconcile cycle
 * (the exact latent bug RM_SAT_022's own text documents) and this module WILL crash the client
 * loudly and repeatably. <b>That's this module doing its job, not a bug in it</b> -- it is the
 * automated proof the gap SAT_039 was opened to close actually exists, until it doesn't.
 *
 * <p>The canary is a vanilla {@code Bat}, referenced only via {@link EntityType#BAT} and handled
 * as a plain {@link Mob} everywhere in this class -- deliberately never imported or type-named as
 * the concrete {@code net.minecraft.world.entity.animal.Bat} class. That's not just defensive:
 * swapping the canary species later (behavioral reasons -- flight, despawn edge cases, whatever
 * "problematic" turns out to mean) becomes a one-line change to {@link #CANARY_TYPE} rather than a
 * type change scattered across every method that touches it.
 *
 * <p>Mechanism, no new raw Forge touch points (everything rides {@code ScopeEvent}, same as every
 * other tracking module):
 * <ol>
 *   <li>A {@code LevelJigConfig} registered {@code BOTH} (mirrors Border's own precedent for a
 *   client-ticking {@code LevelJig} config -- see Jig & Scope Runtime's registration-and-
 *   compilation section) is this module's always-on coordinator, since it gets a real
 *   {@code ScopeEvent.Tick} on both sides independent of whether anything is scoped under
 *   {@code MobJig} yet -- {@code MobJig} itself has no such side-agnostic entry point to hang
 *   this on.</li>
 *   <li><b>Server-side</b>, on the overworld {@code LevelJig} scope loading: find-or-spawn one
 *   tagged, harmless {@code Bat} ({@code setNoAi}/{@code setInvulnerable}/
 *   {@code setPersistenceRequired}, deliberately never killable or wandering off) near world
 *   spawn, then register it the normal way via {@link #watch}. Proves the pre-existing
 *   {@code SERVER}-applicability path still works exactly as {@code MobTrackingModule} already
 *   did -- this module doesn't touch that half's correctness, only its address.</li>
 *   <li><b>Client-side</b>, throttled on the coordinator's own tick: scan for the same tagged bat
 *   by custom name and call {@link MobScope#getFor} on it directly -- the documented fast-path
 *   attachment -- to get a client-side {@code MobScope} onto {@code MobJig}'s machinery without
 *   going through interest registration at all. RM_SAT_022 ("Roger") has since made a
 *   client-side interest-driven path real too (via {@code ForgeEgress}/{@code
 *   ClientForgeEgress}), but this canary still uses the {@code getFor} fast path deliberately --
 *   immediate attachment at spawn time, not waiting on the poll's ~20-tick cadence.</li>
 *   <li><b>The check itself</b> lives in this module's own {@code MOB_JIG} unload handler: a
 *   {@code MobScope} whose backing {@link Mob#isRemoved()} is still {@code false} at teardown time
 *   has been torn down for a reason other than the two legitimate ones (chunk unload, genuine
 *   removal) MobJig's reason-agnostic contract allows -- both of those set {@code isRemoved()}
 *   true by construction. That's a direct violation of the done bar RM_SAT_022 has to meet, not
 *   an inference from log lines.</li>
 * </ol>
 *
 * <p><b>Known limitation, stated rather than solved here:</b> entity tracking to a given client
 * requires that client's player be within tracking range of the canary, not merely that its chunk
 * is server-loaded. Standing near world spawn while connected is what actually exercises the
 * client-side half -- this module can't force that from server-side alone without adding a second
 * mechanism (forced chunk-loading does not imply per-player entity tracking). Worth automating
 * later; not blocking for this pass, since the whole point right now is proving the gap exists at
 * all, by hand once, on a real client.
 *
 * <p><b>Known limitation, LevelJig:</b> the violation check only runs server-side, using {@code
 * MinecraftServer#getLevel} to ask whether the dimension is still actively registered. There's no
 * client-side equivalent here -- the only clean signal would be {@code
 * Minecraft.getInstance().level}, and per this project's Forge Integration & Sidedness Contract,
 * {@code ClientForgeIngress} is the only class meant to touch client-engine state directly; adding
 * that reference here would break that boundary for a check whose value hasn't been weighed
 * against the cost. {@code LevelJig}'s config is still widened to {@code BOTH} (see {@link
 * #registerLevelHealthCheck}), so the client-side load/tick/unload path is genuinely exercised --
 * an exception anywhere in it still crashes loudly -- just not this specific violation check.
 *
 * <p><b>Crash is unconditional for now, by explicit project-owner instruction</b> -- no
 * dev/production gate exists yet to make this safe to ship live. That gate is deliberately left
 * as follow-up scope, not built speculatively against a deployment shape nobody has specified.
 */
public final class SatchelHealth {

    private SatchelHealth() {
    }

    // =================================================================
    // Coordinator: BOTH-applicability LevelJig, always ticks on both
    // sides regardless of whether anything is scoped under MobJig yet.
    // =================================================================

    public static final BundleKey<SatchelBundle> COORDINATOR_BUNDLE =
            new BundleKey<>(
                    "satchelhealth:coordinator_bundle",
                    SatchelBundle.class
            );

    public static final FixtureKey<TrackerFixture> COORDINATOR_TRACKER =
            new FixtureKey<>(
                    "satchelhealth:coordinator_fixture",
                    TrackerFixture.class
            );

    public static final JigKey<LevelJig> COORDINATOR_JIG =
            new JigKey<>(
                    "satchelhealth:coordinator_jig",
                    LevelJig.class
            );

    // =================================================================
    // MobJig health check -- relocated from MobTrackingModule verbatim
    // (same IDs, same watch/unwatch/currentInterests surface), with
    // sideApplicability widened SERVER -> BOTH. That widening is the
    // entire point of this ticket's MobJig slice.
    // =================================================================

    public static final BundleKey<SatchelBundle> MOB_BUNDLE =
            new BundleKey<>(
                    "satchelmobtracker:mob_tracker_bundle",
                    SatchelBundle.class
            );

    public static final FixtureKey<TrackerFixture> MOB_TRACKER =
            new FixtureKey<>(
                    "satchelmobtracker:mob_tracker_fixture",
                    TrackerFixture.class
            );

    public static final JigKey<MobJig> MOB_JIG =
            new JigKey<>(
                    "satchelmobtracker:mob_tracker_jig",
                    MobJig.class
            );

    // =================================================================
    // LevelJig health check -- relocated from TrackingModule verbatim
    // (same IDs, same debug-level LOADED/UNLOADED/TICK logging), with
    // sideApplicability widened SERVER -> BOTH (SAT_040). New here: a
    // server-side violation check on teardown -- see
    // onLevelScopeUnloaded's own docs for why it's server-only.
    // =================================================================

    public static final BundleKey<SatchelBundle> LEVEL_BUNDLE =
            new BundleKey<>(
                    "satcheltracker:tracker_bundle",
                    SatchelBundle.class
            );

    public static final FixtureKey<TrackerFixture> LEVEL_TRACKER =
            new FixtureKey<>(
                    "satcheltracker:tracker_fixture",
                    TrackerFixture.class
            );

    public static final JigKey<LevelJig> LEVEL_JIG =
            new JigKey<>(
                    "satcheltracker:tracker_jig",
                    LevelJig.class
            );

    // =================================================================
    // PlayerJig health check -- relocated from PlayerTrackingModule
    // verbatim (same IDs, same info-level LOADED/UNLOADED/TICK
    // logging). Stays SERVER -- PlayerScope wraps a ServerPlayer
    // directly and has no client-side existence to widen into (see
    // PlayerScope's own class docs). New here: a violation check on
    // teardown -- see onPlayerScopeUnloaded's own docs.
    // =================================================================

    public static final BundleKey<SatchelBundle> PLAYER_BUNDLE =
            new BundleKey<>(
                    "satcheltracker:player_tracker_bundle",
                    SatchelBundle.class
            );

    public static final FixtureKey<TrackerFixture> PLAYER_TRACKER =
            new FixtureKey<>(
                    "satcheltracker:player_tracker_fixture",
                    TrackerFixture.class
            );

    public static final JigKey<PlayerJig> PLAYER_JIG =
            new JigKey<>(
                    "satcheltracker:player_tracker_jig",
                    PlayerJig.class
            );

    // RM_SAT_022 ("Roger"): widened from Map<ServerLevel, Set<UUID>> to Map<Level, Set<UUID>> --
    // MobJig's poll now resolves through ForgeEgress, not a ServerLevel cast, so this registry
    // (and MobInterestSupplier's own signature) can honestly accept either side's Level. Callers
    // that only ever pass a ServerLevel (MobTrackCommands, all server-only chat commands) keep
    // compiling unchanged -- a ServerLevel is still legal wherever a Level is asked for.
    private static final Map<Level, Set<UUID>> INTERESTS = new ConcurrentHashMap<>();

    /**
     * Registers manual interest in {@code uuid} within {@code level} -- unchanged from
     * {@code MobTrackingModule}, still the surface {@code MobTrackCommands} calls.
     */
    public static void watch(Level level, UUID uuid) {
        INTERESTS.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    public static void unwatch(Level level, UUID uuid) {
        Set<UUID> set = INTERESTS.get(level);
        if (set != null) {
            set.remove(uuid);
        }
    }

    public static Map<Level, Set<UUID>> currentInterests() {
        return Map.copyOf(INTERESTS);
    }

    // =================================================================
    // Canary
    // =================================================================

    /**
     * The canary's entity type, referenced only through {@link EntityType} -- nothing in this
     * class imports or type-names the concrete vanilla entity class. See the class docs' note on
     * why; this is the one line to change if the species itself ever needs to change.
     */
    private static final EntityType<? extends Mob> CANARY_TYPE = EntityType.BAT;

    /** Custom-name marker the client-side scan matches on -- see class docs' mechanism list. */
    private static final String CANARY_NAME = "SatchelHealthCanary";

    /**
     * Search radius around the level's real shared spawn point (see {@link #anchorPos}), both
     * for server find-or-spawn and client discovery. Sized to comfortably cover vanilla's default
     * {@code spawnRadius} gamerule (10 blocks) -- a fresh player can legitimately land anywhere
     * within that ring around the shared spawn point, not exactly on top of it.
     */
    private static final double CANARY_SEARCH_RADIUS = 16.0;

    private static final int CLIENT_SCAN_EVERY_N_TICKS = 100; // ~5s at 20 TPS, matches the
    // throttle cadence every other tracking module already uses for its own log/scan output.

    // =================================================================
    // Registration
    // =================================================================

    public static void init() {
        OUT.info(
                "SatchelHealth Initializing (SAT_039/SAT_040 -- absorbs MobTrackingModule "
                        + "[BOTH], TrackingModule [now BOTH], PlayerTrackingModule [SERVER])"
        );

        registerCoordinator();
        registerMobHealthCheck();
        registerLevelHealthCheck();
        registerPlayerHealthCheck();
    }

    private static void registerCoordinator() {
        var coordinatorFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        COORDINATOR_TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var coordinatorBundleDecl =
                new JigBundles.BundleDecl<LevelScope, SatchelBundle>(
                        COORDINATOR_BUNDLE,
                        (LevelScope scope) -> new SatchelBundle(scope, COORDINATOR_BUNDLE),
                        List.of(coordinatorFixture)
                );

        JigBundles.Schema<LevelScope> bundles =
                new JigBundles.Schema<>(List.of(coordinatorBundleDecl));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, SatchelHealth::onCoordinatorTick)
                        .build();

        LevelJigConfig config = new LevelJigConfig(COORDINATOR_JIG);

        config.bundles().schema(bundles);

        // BOTH -- the coordinator has to tick on the client too; that's the whole reason it
        // exists rather than piggybacking on MOB_JIG directly (MobJig has no tick source
        // independent of already having a scope -- see class docs, mechanism item 1).
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    private static void registerMobHealthCheck() {
        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        MOB_TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<MobScope, SatchelBundle>(
                        MOB_BUNDLE,
                        (MobScope scope) -> new SatchelBundle(scope, MOB_BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<MobScope> bundles =
                new JigBundles.Schema<>(List.of(trackingBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, SatchelHealth::onMobScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, SatchelHealth::onMobScopeUnloaded)
                        .on(ScopeEvent.Tick.class, SatchelHealth::onMobScopeTick)
                        .build();

        MobJigConfig config = new MobJigConfig(MOB_JIG);

        // Was SERVER (MobTrackingModule's original call). BOTH is this ticket's actual change --
        // see class docs. Everything downstream of this one line is what makes that change
        // provable rather than just declared.
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);

        config.bundles().schema(bundles);

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        MobInterestRegistry.register(MOB_JIG, () -> INTERESTS);

        Satchel.registerJigConfig(config);
    }

    private static void registerLevelHealthCheck() {
        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        LEVEL_TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<LevelScope, SatchelBundle>(
                        LEVEL_BUNDLE,
                        (LevelScope scope) -> new SatchelBundle(scope, LEVEL_BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<LevelScope> bundles =
                new JigBundles.Schema<>(List.of(trackingBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, SatchelHealth::onLevelScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, SatchelHealth::onLevelScopeUnloaded)
                        .on(ScopeEvent.Tick.class, SatchelHealth::onLevelScopeTick)
                        .build();

        LevelJigConfig config = new LevelJigConfig(LEVEL_JIG);

        // Was SERVER (TrackingModule's original call, LevelJigConfig's own default). BOTH is
        // SAT_040's change here, same treatment SAT_039 gave MobJig and for the same reason: a
        // server/client pair running only Satchel (no FrontierMode, no Border) should still be
        // able to prove LevelJig's client-side lifecycle path works, rather than depending on
        // Border to be the only thing that's ever exercised it.
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);

        config.bundles().schema(bundles);

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    private static void registerPlayerHealthCheck() {
        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        PLAYER_TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<PlayerScope, SatchelBundle>(
                        PLAYER_BUNDLE,
                        (PlayerScope scope) -> new SatchelBundle(scope, PLAYER_BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<PlayerScope> bundles =
                new JigBundles.Schema<>(List.of(trackingBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, SatchelHealth::onPlayerScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, SatchelHealth::onPlayerScopeUnloaded)
                        .on(ScopeEvent.Tick.class, SatchelHealth::onPlayerScopeTick)
                        .build();

        // PlayerJigConfig already pins sideApplicability(SERVER) as its own default -- left
        // untouched here, deliberately: PlayerScope wraps a ServerPlayer directly and has no
        // client-side existence to widen into (see PlayerScope's own class docs), unlike
        // MobJig/LevelJig which both had a real CLIENT/BOTH gap to close.
        PlayerJigConfig config = new PlayerJigConfig(PLAYER_JIG);

        config.bundles().schema(bundles);

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    // =================================================================
    // Coordinator handlers -- canary spawn (server) / discovery (client)
    // =================================================================

    private static void onCoordinatorTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        // Shared-bus caveat, same reasoning every tracking module's handlers already carry:
        // ScopeEvent.Tick fires for every jig scoped to whatever just ticked, not just this one.
        if (!COORDINATOR_JIG.equals(info.jigInfo().key)) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        LogicalSide side = Satchel.require().side();

        if (side == LogicalSide.SERVER) {
            if (level instanceof ServerLevel serverLevel) {
                findOrSpawnCanary(serverLevel);
            }
            return;
        }

        if (side == LogicalSide.CLIENT) {
            // Throttled -- a real per-tick scan every tick would be wasted work; this only needs
            // to notice the canary once it's in tracking range, not every 1/20th of a second.
            scanForCanaryOnClient(level);
        }
    }

    private static final Map<Level, Integer> clientScanCounters = new ConcurrentHashMap<>();

    private static boolean isCanary(Mob mob) {
        return mob.getType() == CANARY_TYPE
                && mob.getCustomName() != null
                && CANARY_NAME.equals(mob.getCustomName().getString());
    }

    private static void scanForCanaryOnClient(Level level) {
        int count = clientScanCounters.merge(level, 1, Integer::sum);
        if (count % CLIENT_SCAN_EVERY_N_TICKS != 0) {
            return;
        }

        AABB box = searchBoxAround(level);
        List<Mob> found = level.getEntitiesOfClass(Mob.class, box, SatchelHealth::isCanary);

        for (Mob canary : found) {
            // MobScope.getFor's own contract: idempotent (via JigInfo.hasScope), safe to call
            // every time this scan finds the canary, scoped or not.
            MobScope.getFor(canary);
        }
    }

    private static void findOrSpawnCanary(ServerLevel level) {
        AABB box = searchBoxAround(level);
        List<Mob> existing = level.getEntitiesOfClass(Mob.class, box, SatchelHealth::isCanary);

        Mob canary;
        if (!existing.isEmpty()) {
            canary = existing.get(0);
        } else {
            var spawnPos = anchorPos(level);
            canary = CANARY_TYPE.create(level);
            Objects.requireNonNull(canary, "CANARY_TYPE.create(level) returned null");
            canary.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            canary.setCustomName(Component.literal(CANARY_NAME));
            canary.setCustomNameVisible(false);
            canary.setInvulnerable(true);
            canary.setNoAi(true);
            canary.setSilent(true);
            canary.setPersistenceRequired();
            level.addFreshEntity(canary);
            OUT.info("[SatchelHealth] Canary spawned at " + spawnPos
                    + " in " + level.dimension().location());
        }

        watch(level, canary.getUUID());
    }

    private static BlockPos anchorPos(Level level) {
        // The level's actual shared spawn point, not a guessed/hardcoded coordinate -- a fixed
        // (0, -60, 0) was tried first and turned out to sit nowhere near this world's real spawn
        // (observed ~130 blocks off, both horizontally and vertically, on a real run), which
        // meant a player logging in near spawn -- the normal case -- never got within the
        // canary's search radius or client tracking range at all. Level#getSharedSpawnPos() is
        // available on both ServerLevel and the client's Level (synced from the server), so this
        // resolves consistently on both sides without needing a side-specific lookup. Not
        // force-loaded by this module either way (see class docs' "known limitation"); a
        // dev/test server that keeps spawn chunks loaded keeps this canary tickable, one that
        // doesn't will simply not run this check until a player is nearby, which is a
        // degraded-coverage state worth noticing, not a crash.
        return level.getSharedSpawnPos();
    }

    private static AABB searchBoxAround(Level level) {
        var pos = anchorPos(level);
        return new AABB(
                pos.getX() - CANARY_SEARCH_RADIUS, pos.getY() - CANARY_SEARCH_RADIUS, pos.getZ() - CANARY_SEARCH_RADIUS,
                pos.getX() + CANARY_SEARCH_RADIUS, pos.getY() + CANARY_SEARCH_RADIUS, pos.getZ() + CANARY_SEARCH_RADIUS
        );
    }

    // =================================================================
    // MOB_JIG handlers -- relocated MobTrackingModule logic, plus the
    // one new thing this ticket exists to add: onMobScopeUnloaded's
    // violation check.
    // =================================================================

    private static void onMobScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!MOB_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        var bundle = jig.getOrCreate(info.scope(), MOB_BUNDLE);
        var tracker = bundle.getOrCreateFixture(MOB_TRACKER, TrackerFixture::new);
        tracker.countLoaded();

        MobScope scope = (MobScope) info.scope();
        OUT.info(
                "[SatchelHealth] LOADED mob=" + scope.mob().getType()
                        + " scope=" + scope.debugName()
                        + " side=" + Satchel.require().side()
                        + " dim=" + scope.mob().level().dimension().location()
        );
    }

    /**
     * The check this whole ticket exists to build. A {@link MobScope} that reaches teardown with
     * its backing {@link Mob#isRemoved()} still {@code false} has been torn down for a reason
     * other than the two MobJig's reason-agnostic contract allows (chunk unload, genuine removal
     * -- both set {@code isRemoved()} true by construction; see Jig & Scope Runtime's MobJig
     * section and {@link com.arryn.satchel.common.jig.mob.MobJig#reconcile}'s own docs). That is
     * exactly the shape of the latent client-side bug RM_SAT_022 documents and exists to fix --
     * until it lands, this fires on the very first client-side canary teardown.
     */
    private static void onMobScopeUnloaded(ScopeEvent.Unloaded event) {
        ScopeInfo info = event.info();

        if (!MOB_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        MobScope scope = (MobScope) info.scope();
        Mob mob = scope.mob();
        LogicalSide side = Satchel.require().side();

        jig.ask(info.scope(), MOB_BUNDLE)
                .flatMap(b -> b.get(MOB_TRACKER))
                .ifPresent(TrackerFixture::countUnloaded);

        OUT.info("[SatchelHealth] UNLOADED scope=" + scope.debugName() + " side=" + side);

        if (!mob.isRemoved()) {
            String message = "[SatchelHealth] VIOLATION: MobScope torn down on side=" + side
                    + " while its backing mob is still present (isRemoved()=false). "
                    + "mob=" + mob.getType() + " uuid=" + scope.uuid()
                    + " -- this is the RM_SAT_022 latent teardown bug (or a new regression of the "
                    + "same shape) caught live. See SAT_039 / RM_SAT_022 before treating this as "
                    + "a surprise.";
            OUT.error(message);
            throw new IllegalStateException(message);
        }
    }

    private static final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private static final int MOB_TICK_LOG_EVERY_N_TICKS = 100;

    private static void onMobScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();

        if (!MOB_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        MobScope scope = (MobScope) info.scope();

        jig.ask(info.scope(), MOB_BUNDLE)
                .flatMap(b -> b.get(MOB_TRACKER))
                .ifPresent(f -> {
                    f.countExternalTick();

                    int count = tickCounters.merge(scope.uuid(), 1, Integer::sum);
                    if (count % MOB_TICK_LOG_EVERY_N_TICKS == 0) {
                        OUT.info(
                                "[SatchelHealth] TICK mob=" + scope.mob().getType()
                                        + " scope=" + scope.debugName()
                                        + " side=" + Satchel.require().side()
                        );
                    }
                });
    }

    // =================================================================
    // SAT_040's genuinely-deferred health-check queue (second attempt, 2026-08-24). Used by
    // both onLevelScopeUnloaded and onPlayerScopeUnloaded below -- see either method's own docs
    // for why this exists and why the first attempt (server.execute(...)) didn't work. Plain
    // ArrayDeque, not a concurrent collection: every enqueue happens from a Forge event fired on
    // the server thread, and the only drain point (pumpPendingHealthChecks, called from
    // ServerForgeIngress's own execution-pulse tick hook) also runs on the server thread.
    // =================================================================
    private static final Deque<Runnable> PENDING_HEALTH_CHECKS = new ArrayDeque<>();

    /**
     * Called once per server tick by {@code ServerForgeIngress#onExecutionPulse}. Drains and
     * runs every check queued since the last pump. Drains the whole queue every call rather than
     * just the head -- these checks are cheap (a single map/list lookup each) and there's no
     * reason to spread them across multiple ticks.
     */
    public static void pumpPendingHealthChecks() {
        Runnable check;
        while ((check = PENDING_HEALTH_CHECKS.poll()) != null) {
            check.run();
        }
    }

    // =================================================================
    // LEVEL_JIG handlers -- relocated TrackingModule logic (unchanged:
    // still DEBUG-level, still logs "[Tracking] ..."), plus SAT_040's
    // new addition: onLevelScopeUnloaded's violation check.
    // =================================================================

    private static void onLevelScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        // Shared-bus caveat, same reasoning every tracking module's handlers already carry.
        if (!LEVEL_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        var bundle = jig.getOrCreate(info.scope(), LEVEL_BUNDLE);
        var tracker = bundle.getOrCreateFixture(LEVEL_TRACKER, TrackerFixture::new);
        tracker.countLoaded();

        OUT.debug("[Tracking] LOADED " + info.debugName() + " " + tracker);
    }

    /**
     * SAT_040's addition. Unlike {@link #onMobScopeUnloaded}, {@code LevelJig} teardown isn't
     * <i>inferred</i> from a poll -- {@code ServerForgeIngress#onLevelUnload} is the only place
     * that calls {@code tryRemoveSource(level)}, and it only runs from a real {@code
     * LevelEvent.Unload}. So the violation this check catches isn't "is the dimension actually
     * gone" (trivially true given how teardown is wired today) but "did teardown get triggered
     * for a dimension that's still the server's actively-registered instance for that key" --
     * exactly what a future regression that calls {@code tryRemoveSource} from the wrong place
     * (a stray extra hook, a stale-reference bug like RM_SAT_014's) would produce. Server-side
     * only -- see this class's own docs for why there's no client-side equivalent yet.
     *
     * <p><b>Second attempt at deferral, 2026-08-24 -- the first ({@code server.execute(...)})
     * didn't actually defer anything.</b> {@code BlockableEventLoop#execute} runs its task
     * INLINE when called from the thread that already owns the loop -- and this whole call
     * chain ({@code MinecraftServer.stopServer()} -> {@code LevelEvent.Unload} -> this handler)
     * always runs on the server thread, so the "deferred" check fired synchronously, same as the
     * very first (undeferred) version, and false-positived the same way. This version instead
     * queues the check onto {@link #PENDING_HEALTH_CHECKS}, drained once per server tick from a
     * genuinely later call chain ({@code ServerForgeIngress#onExecutionPulse}, the next tick's
     * execution pulse) -- not from inside the same synchronous unwind that queued it. On final
     * process shutdown specifically, the tick loop has already stopped before {@code
     * stopServer()} fires this event, so the queue is never pumped again and the check is
     * silently abandoned rather than firing wrongly -- correct behavior, not a gap: nothing is
     * left running to observe a stale registration once the whole server is going away anyway.
     */
    private static void onLevelScopeUnloaded(ScopeEvent.Unloaded event) {
        ScopeInfo info = event.info();

        if (!LEVEL_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        LevelScope scope = (LevelScope) info.scope();

        jig.ask(info.scope(), LEVEL_BUNDLE)
                .flatMap(b -> b.get(LEVEL_TRACKER))
                .ifPresent(f -> {
                    f.countUnloaded();
                    OUT.debug("[Tracking] UNLOADED " + info.debugName() + " " + f);
                });

        Level level = scope.level();
        if (level instanceof ServerLevel serverLevel) {
            var server = serverLevel.getServer();
            var dimension = serverLevel.dimension();

            PENDING_HEALTH_CHECKS.add(() -> {
                boolean stillRegistered = server.getLevel(dimension) == serverLevel;

                if (stillRegistered) {
                    String message = "[SatchelHealth] VIOLATION: LevelScope torn down for "
                            + "dimension=" + dimension.location() + " and it is STILL the "
                            + "server's actively-registered level instance for that dimension "
                            + "key one tick later. Teardown should only ever follow a real "
                            + "LevelEvent.Unload for this exact level (see "
                            + "ServerForgeIngress#onLevelUnload) -- something called "
                            + "tryRemoveSource for a level that never actually unloaded.";
                    OUT.error(message);
                    throw new IllegalStateException(message);
                }
            });
        }
    }

    private static void onLevelScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();

        if (!LEVEL_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;

        jig.ask(info.scope(), LEVEL_BUNDLE)
                .flatMap(b -> b.get(LEVEL_TRACKER))
                .ifPresent(TrackerFixture::countExternalTick);
    }

    // =================================================================
    // PLAYER_JIG handlers -- relocated PlayerTrackingModule logic
    // (unchanged: still INFO-level, still logs "[PlayerTracking] ..."),
    // plus SAT_040's new addition: onPlayerScopeUnloaded's violation
    // check.
    // =================================================================

    private static void onPlayerScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!PLAYER_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        var bundle = jig.getOrCreate(info.scope(), PLAYER_BUNDLE);
        var tracker = bundle.getOrCreateFixture(PLAYER_TRACKER, TrackerFixture::new);
        tracker.countLoaded();

        PlayerScope scope = (PlayerScope) info.scope();
        OUT.info(
                "[PlayerTracking] LOADED player=" + scope.player().getGameProfile().getName()
                        + " scope=" + scope.debugName()
                        + " dim=" + scope.player().serverLevel().dimension().location()
        );
    }

    /**
     * SAT_040's addition, same reasoning as {@link #onLevelScopeUnloaded}: {@code PlayerJig}
     * teardown is authoritative, not inferred -- {@code ServerForgeIngress#onPlayerScopeUnload}
     * is the only place that calls {@code tryRemoveSource(player)}, and it only runs from a real
     * {@code PlayerLoggedOutEvent}. The violation this check catches is teardown firing while the
     * player is still in the server's own online-player list -- exactly what would happen if a
     * future regression wired {@code tryRemoveSource} to something else that isn't a real logout
     * (e.g. {@code PlayerChangedDimensionEvent}, which {@code ServerForgeIngress}'s own comment
     * already flags as deliberately NOT hooked to this -- an easy mistake for someone to
     * reintroduce later without this check in place).
     *
     * <p><b>Second attempt at deferral, 2026-08-24 -- see {@link #onLevelScopeUnloaded}'s docs
     * for why the first ({@code server.execute(...)}) didn't actually defer anything.</b> This
     * version queues the check onto {@link #PENDING_HEALTH_CHECKS} instead, drained once per
     * server tick from a genuinely later call chain. For an ordinary logout, {@code
     * PlayerList#remove} finishes removing the player from its own lists well before the next
     * tick's execution pulse runs, so the queued check now actually observes the post-removal
     * state. On full shutdown, the tick loop has already stopped before the remaining players
     * get kicked, so the queue never gets pumped again and the check is silently abandoned --
     * correct here too, for the same reason as the level check.
     */
    private static void onPlayerScopeUnloaded(ScopeEvent.Unloaded event) {
        ScopeInfo info = event.info();

        if (!PLAYER_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        PlayerScope scope = (PlayerScope) info.scope();

        jig.ask(info.scope(), PLAYER_BUNDLE)
                .flatMap(b -> b.get(PLAYER_TRACKER))
                .ifPresent(TrackerFixture::countUnloaded);

        playerTickCounters.remove(scope.uuid());

        OUT.info(
                "[PlayerTracking] UNLOADED player=" + scope.player().getGameProfile().getName()
                        + " scope=" + scope.debugName()
        );

        ServerPlayer player = scope.player();
        var server = player.getServer();

        if (server != null) {
            UUID playerUuid = player.getUUID();
            String playerName = player.getGameProfile().getName();

            PENDING_HEALTH_CHECKS.add(() -> {
                boolean stillOnline = server.getPlayerList().getPlayer(playerUuid) != null;

                if (stillOnline) {
                    String message = "[SatchelHealth] VIOLATION: PlayerScope torn down for "
                            + "player=" + playerName + " and they are STILL present in the "
                            + "server's online player list one tick later. Teardown should "
                            + "only ever follow a real PlayerLoggedOutEvent (see "
                            + "ServerForgeIngress#onPlayerScopeUnload) -- something called "
                            + "tryRemoveSource for a player who never actually logged out.";
                    OUT.error(message);
                    throw new IllegalStateException(message);
                }
            });
        }
    }

    // Per-scope tick counter, keyed by scope UUID, purely for throttling this debug tool's own
    // log output -- relocated from PlayerTrackingModule unchanged. Named distinctly from MobJig's
    // own tickCounters above (both classes' state now lives in this one file).
    private static final Map<UUID, Integer> playerTickCounters = new ConcurrentHashMap<>();
    private static final int PLAYER_TICK_LOG_EVERY_N_TICKS = 100; // ~5s at 20 TPS

    private static void onPlayerScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();

        if (!PLAYER_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        PlayerScope scope = (PlayerScope) info.scope();

        jig.ask(info.scope(), PLAYER_BUNDLE)
                .flatMap(b -> b.get(PLAYER_TRACKER))
                .ifPresent(f -> {
                    f.countExternalTick();

                    int count = playerTickCounters.merge(scope.uuid(), 1, Integer::sum);
                    if (count % PLAYER_TICK_LOG_EVERY_N_TICKS == 0) {
                        OUT.info(
                                "[PlayerTracking] TICK player=" + scope.player().getGameProfile().getName()
                                        + " scope=" + scope.debugName()
                                        + " dim=" + scope.player().serverLevel().dimension().location()
                        );
                    }
                });
    }
}
