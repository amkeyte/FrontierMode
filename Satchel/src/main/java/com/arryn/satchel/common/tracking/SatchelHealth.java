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
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.EventHandlers;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.TrackerFixture;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.LevelJigConfig;
import com.arryn.satchel.common.newconfig.newnew.MobJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.LogicalSide;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SAT_039 -- the intended home for every run-monitoring / self-verification module Satchel owns,
 * not a single-purpose test. Absorbs {@code MobTrackingModule} wholesale (same registered bundle/
 * fixture/jig key IDs, same {@code watch}/{@code unwatch}/{@code currentInterests} surface for
 * {@code MobTrackCommands} -- this is a relocation, not a second copy) and adds the one thing
 * SAT_039 exists to build: an automatic, silent-on-pass, loud-on-fail regression backstop for
 * MobJig's CLIENT/BOTH path, which nothing in either repo has ever exercised (see SAT_039's audit
 * and RM_SAT_022's log). {@code LevelJig}/{@code PlayerJig} health absorption is deliberately not
 * done in this pass -- SAT_039's follow-up ticket, opened alongside this one.
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
 *   needing a client-side interest-registration mechanism, which doesn't exist yet (that's
 *   {@code MobEntityLookup}'s job, RM_SAT_022's own build).</li>
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

    private static final Map<ServerLevel, Set<UUID>> INTERESTS = new ConcurrentHashMap<>();

    /**
     * Registers manual interest in {@code uuid} within {@code level} -- unchanged from
     * {@code MobTrackingModule}, still the surface {@code MobTrackCommands} calls.
     */
    public static void watch(ServerLevel level, UUID uuid) {
        INTERESTS.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    public static void unwatch(ServerLevel level, UUID uuid) {
        Set<UUID> set = INTERESTS.get(level);
        if (set != null) {
            set.remove(uuid);
        }
    }

    public static Map<ServerLevel, Set<UUID>> currentInterests() {
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

    /** Search radius around world spawn, both for server find-or-spawn and client discovery. */
    private static final double CANARY_SEARCH_RADIUS = 8.0;

    private static final int CLIENT_SCAN_EVERY_N_TICKS = 100; // ~5s at 20 TPS, matches the
    // throttle cadence every other tracking module already uses for its own log/scan output.

    // =================================================================
    // Registration
    // =================================================================

    public static void init() {
        OUT.info("SatchelHealth Initializing (SAT_039 -- absorbs MobTrackingModule, MobJig now BOTH)");

        registerCoordinator();
        registerMobHealthCheck();
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
        // Fixed offset near world origin, well clear of normal build height (-64..320 in
        // 1.20.1) but still a real, server-loadable position -- not force-loaded by this module
        // (see class docs' "known limitation"). A dev/test server that keeps spawn chunks loaded
        // keeps this canary tickable; one that doesn't will simply not run this check until a
        // player is nearby, which is a degraded-coverage state worth noticing, not a crash.
        return new BlockPos(0, -60, 0);
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
}
