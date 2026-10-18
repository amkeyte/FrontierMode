package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobInterestRegistry;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.mob.MobJigConfig;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.LogicalSide;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MobJig's slice of SatchelHealth -- see {@link SatchelHealth} for the shared
 * init/watch/pending-check machinery this registers into. Relocated from the single
 * SatchelHealth.java (SAT_060) once that file grew too large to keep editing as one piece --
 * same IDs, same behavior as before the split, not a rewrite.
 */
final class MobHealthCheck {

    private MobHealthCheck() {}

    public static final BundleKey<MobTrackerBundle> MOB_BUNDLE =
            new BundleKey<>(
                    "satchelmobtracker:mob_tracker_bundle",
                    MobTrackerBundle.class
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

    static void register() {
        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        MOB_TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<MobScope, MobTrackerBundle>(
                        MOB_BUNDLE,
                        (MobScope scope) -> new MobTrackerBundle(scope, MOB_BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<MobScope> bundles =
                new JigBundles.Schema<>(List.of(trackingBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, MobHealthCheck::onMobScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, MobHealthCheck::onMobScopeUnloaded)
                        .on(ScopeEvent.Tick.class, MobHealthCheck::onMobScopeTick)
                        .build();

        MobJigConfig config = new MobJigConfig(MOB_JIG);

        // Was SERVER (MobTrackingModule's original call). BOTH is what makes MobJig's CLIENT/BOTH
        // gap provable rather than just declared.
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);

        // Required for PERSISTENCE below to actually work, not optional -- same gap and same
        // fix as PlayerHealthCheck's own (SAT_063's log has the full story): confirmed live,
        // ScopeEngine_Server.resolveServerLevel() throws AccessFailed when info.referenceLevel()
        // is null, and MobJigConfig sets no default (see its own doc comment -- this was already
        // a known, named gap, just not actually closed here until this crash). Mob#level()
        // already returns the general Level type, no cast needed here -- resolveServerLevel()
        // itself does the instanceof ServerLevel check downstream, same as it does for every
        // other jig kind's resolver.
        config.binding().referenceLevel(scope -> scope.mob().level());

        config.bundles().schema(bundles);

        config.policies().capabilities(new JigPolicies.Capabilities(true, false, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);

        MobInterestRegistry.registerPerLevel(MOB_JIG, SatchelHealth::interestsIn);

        Satchel.registerJigConfig(config);
    }

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
        OUT.debug(
                "[SatchelHealth] LOADED mob=" + scope.mob().getType()
                        + " scope=" + scope.debugName()
                        + " side=" + Satchel.require().side()
                        + " dim=" + scope.mob().level().dimension().location()
        );
    }

    /**
     * A mob scope unloads on its mob's leave event, which can come while {@link Mob#isRemoved()} is
     * still {@code false}: a chunk's tracking ends before the removal is recorded. So the old rule,
     * "torn down while the mob is still present", is not a violation any more. The invariant is the
     * leak: see {@link #onMobScopeTick}. This handler counts the unload, forgets the leak counter, and
     * logs the reason the scope carried, which is how a real chunk unload's reason (UNLOADED or
     * UNKNOWN) is found.
     */
    private static void onMobScopeUnloaded(ScopeEvent.Unloaded event) {
        ScopeInfo info = event.info();

        if (!MOB_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        MobScope scope = (MobScope) info.scope();
        LogicalSide side = Satchel.require().side();

        jig.ask(info.scope(), MOB_BUNDLE)
                .flatMap(b -> b.get(MOB_TRACKER))
                .ifPresent(TrackerFixture::countUnloaded);

        ABSENT.remove(side + ":" + scope.uuid());

        OUT.debug("[SatchelHealth] UNLOADED scope=" + scope.debugName() + " side=" + side
                + " reason=" + info.leaveReason());

        // Checkpoint, not routine noise -- specifically the canary's own teardown, not every
        // ambient mob real gameplay happens to put under tracking (those stay .debug above,
        // same as load -- a busy server can tear down many ordinary tracked mobs per minute).
        if (isCanary(scope)) {
            OUT.info(
                    "[SatchelHealth] MobJig canary unloaded: side=" + side
                            + " uuid=" + Ids.shortId(scope.uuid())
                            + " reason=" + info.leaveReason()
                            + " mobRemoved=" + scope.mob().isRemoved()
            );
        }
    }

    /**
     * Mob scopes the leak check is counting against: {@code side:uuid} to how many checks in a row
     * found the mob absent from the lookup.
     */
    private static final Map<String, Integer> ABSENT = new ConcurrentHashMap<>();

    /** The leak check runs every this many ticks, and a scope is leaked after this many misses. */
    private static final int LEAK_CHECK_EVERY = 10;
    private static final int LEAK_MISSES = 3;

    /**
     * Counts the tick, and runs the leak invariant: a scope alive after its mob has been absent from
     * the lookup for more than a few ticks. A scope unloads on its mob's leave event or once the mob
     * is removed, so one still alive while the lookup can no longer find its mob has missed both.
     */
    private static void onMobScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();

        if (!MOB_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        MobScope scope = (MobScope) info.scope();

        jig.ask(info.scope(), MOB_BUNDLE)
                .flatMap(b -> b.get(MOB_TRACKER))
                .ifPresent(TrackerFixture::countExternalTick);

        if (info.jigInfo().trinketTick().tickNumber() % LEAK_CHECK_EVERY != 0) {
            return;
        }

        LogicalSide side = info.jigInfo().side();
        String key = side + ":" + scope.uuid();
        Mob mob = scope.mob();
        boolean present = info.jigInfo().foundation().egress().getEntity(mob.level(), scope.uuid()).isPresent();
        if (present) {
            ABSENT.remove(key);
            return;
        }

        int misses = ABSENT.merge(key, 1, Integer::sum);
        if (misses > LEAK_MISSES) {
            SatchelHealth.violation("[SatchelHealth] VIOLATION: MobScope leaked on side=" + side
                    + ": its mob " + mob.getType() + " " + Ids.shortId(scope.uuid()) + " has been absent from "
                    + "the lookup for " + (misses * LEAK_CHECK_EVERY) + "+ ticks and the scope is still alive "
                    + "(isRemoved()=" + mob.isRemoved() + "). A scope unloads on its mob's leave event or "
                    + "once the mob is removed; this one missed both.");
        }
    }

    /**
     * The reset check: when a server starts after another stopped in this JVM, no mob scope and no
     * interest may have been carried over. Called as the new server is about to start; the foundation
     * is reset as the previous one stopped.
     */
    static void checkNothingCarriedOver() {
        Satchel.foundationFor(LogicalSide.SERVER).ifPresent(foundation -> {
            int scopes = 0;
            int interests = 0;
            for (var ji : foundation.jigInfos()) {
                if (ji.jig instanceof MobJig mobJig) {
                    scopes += ji.scopeInfos().size();
                    interests += mobJig.interestCount();
                }
            }
            if (scopes != 0 || interests != 0) {
                SatchelHealth.violation("[SatchelHealth] VIOLATION: a new server is starting with " + scopes
                        + " mob scope(s) and " + interests + " interest entr(ies) carried over from the last one."
                        + " The foundation resets every jig as a server stops.");
                return;
            }
            OUT.info("[SatchelHealth] Reset check passed: no mob scope or interest was carried over into the "
                    + "new server");
        });
    }

    // =================================================================
    // Canary -- SAT_060 rebuild. SAT_039's original canary was removed by SAT_050 after a
    // NoSuchFieldError: BAT class-load crash, traced to EntityType.BAT being referenced from an
    // eagerly-evaluated static final field (evaluated at class-init the instant any static
    // member of this class was touched, regardless of whether canary logic ever ran). Fixed
    // here by never assigning the entity type to a static field at all -- see
    // canaryEntityType()'s own doc.
    // =================================================================

    /**
     * Whether {@code scope} is this world's canary.
     */
    private static boolean isCanary(MobScope scope) {
        return SatchelHealth.canaryUuid().filter(scope.uuid()::equals).isPresent();
    }

    /**
     * Ensures a live canary mob exists under {@code MOB_JIG}, so its CLIENT/BOTH poll path has
     * a guaranteed-present subject to exercise continuously rather than depending on real
     * gameplay putting a mob under tracking.
     *
     * <p>
     * Piggybacks on {@link LevelHealthCheck}'s own {@code LEVEL_JIG} tick, not {@code MOB_JIG}'s
     * -- {@code MOB_JIG} has zero scopes whenever no canary currently exists, so it can never
     * wake itself up to spawn one, while {@code LEVEL_JIG} always has a scope for every loaded
     * level, giving this a reliable clock to ride. Subscribed alongside {@link
     * LevelHealthCheck}'s own handlers on the shared event bus (see {@link
     * LevelHealthCheck#register()}), so this guards against {@code LEVEL_JIG}, not {@code
     * MOB_JIG} -- the usual jig-key guard, just checking a different jig than the rest of this
     * file's handlers do.
     *
     * <p>
     * Once spawned, the canary needs no further scanning/rediscovery logic of its own:
     * registering its UUID via {@link SatchelHealth#watch} feeds {@code MOB_JIG}'s own
     * interest-driven poll ({@code MobInterestRegistry}), which already resolves interested
     * UUIDs through {@code ForgeEgress} on both sides independently -- that poll, not anything
     * specific to this method, is what actually proves the CLIENT/BOTH path.
     */
    static void maintainCanary(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();

        if (!LevelHealthCheck.LEVEL_JIG.equals(info.jigInfo().key)) {
            return;
        }

        if (!(info.scope() instanceof LevelScope levelScope)) {
            return;
        }

        if (!(levelScope.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Pinned to the Overworld -- the only dimension guaranteed to have a keep-loaded spawn
        // region with nobody in it. A canary spawned in the Nether/End (no player, no force-load
        // ticket) can go chunk-unresolvable between MobJig.reconcile() polls without ever being
        // isRemoved(), which reads as a false teardown violation rather than an unsuitable spawn
        // site. SatchelHealth as a whole assumes Overworld context for now; per-dimension coverage
        // is a future refactor, not attempted here. Checked before the throttle below so a
        // non-Overworld tick doesn't consume the throttle window.
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        HealthState state = SatchelHealth.state();

        if (!state.canaryCheck.allow()) {
            return;
        }

        UUID existing = state.canaryUuid;
        if (existing != null
                && Satchel.require().egress().getEntity(serverLevel, existing).isPresent()) {
            return;
        }

        Mob canary = spawnCanary(serverLevel);
        if (canary == null) {
            return;
        }

        UUID canaryUuid = canary.getUUID();
        state.canaryUuid = canaryUuid;
        SatchelHealth.watch(serverLevel, canaryUuid);
        MobScope.getFor(canary);

        OUT.info(
                "[SatchelHealth] Canary spawned: " + canary.getType()
                        + " uuid=" + Ids.shortId(canaryUuid)
                        + " dim=" + serverLevel.dimension().location()
        );
    }

    private static Mob spawnCanary(ServerLevel level) {
        Mob mob = canaryEntityType().create(level);
        if (mob == null) {
            return null;
        }

        BlockPos pos = level.getSharedSpawnPos();
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        mob.setPersistenceRequired();

        if (!level.addFreshEntity(mob)) {
            return null;
        }

        return mob;
    }

    /**
     * Deliberately a method, not a {@code static final} field -- this is the actual fix for
     * SAT_050's {@code NoSuchFieldError: BAT} crash. A method body isn't evaluated until called;
     * {@link EntityType#BAT} is only ever touched from inside {@link #spawnCanary}'s own call to
     * this method, itself only reached once {@link #maintainCanary} has already decided a
     * canary needs spawning -- never at class-load time, regardless of whether any static
     * member of this class has been touched.
     */
    private static EntityType<Bat> canaryEntityType() {
        return EntityType.BAT;
    }
}
