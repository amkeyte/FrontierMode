package com.arryn.satchel.common.newconfig;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobInterestRegistry;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.MobJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RM_SAT_021 ("Frank") verification aid, mirroring {@link PlayerTrackingModule}'s already-
 * established role for {@code PlayerJig} exactly: a real, minimal jig consumer whose whole job is
 * proving the jig kind's lifecycle actually fires -- since Boss (the first planned real
 * {@code MobJig} consumer, RM_FRO_018) hasn't landed yet. Not a throwaway hack -- same
 * permanent-diagnostic-infrastructure role {@code TrackingModule}/{@code PlayerTrackingModule}
 * already play for {@code LevelJig}/{@code PlayerJig}.
 *
 * <p>
 * {@link #watch}/{@link #unwatch} register manual interest for exercising the done bar's
 * checklist by hand -- there is no automatic entity-spawn hook here, deliberately: reacting to a
 * spawn event would mean a new raw Forge subscription outside
 * {@code ServerForgeIngress}/{@code ClientForgeIngress}, the only two classes permitted to touch
 * raw Forge events. That's a separate, later change (Boss's own ingress), not this module's job.
 *
 * <p>
 * Logs at INFO (not DEBUG) so the done bar's checklist items are readable straight out of
 * {@code latest.log}:
 * <ul>
 *   <li><b>A watched UUID becoming resolvable creates a scope within one poll cycle</b> -- not
 *   immediately -- exactly one {@code [MobTracking] LOADED} line, naming the mob and its
 *   {@code MobScope} UUID.</li>
 *   <li><b>The tick pulse reaches the scope:</b> {@code [MobTracking] TICK} lines, throttled.</li>
 *   <li><b>The entity no longer resolving tears the scope down within one cycle, for both causes
 *   (chunk unload and genuine removal):</b> exactly one {@code [MobTracking] UNLOADED} line, and
 *   no further {@code TICK} lines for that UUID afterward.</li>
 * </ul>
 */
public final class MobTrackingModule {

    public static final BundleKey<SatchelBundle> BUNDLE =
            new BundleKey<>(
                    "satchelmobtracker:mob_tracker_bundle",
                    SatchelBundle.class
            );

    public static final FixtureKey<TrackerFixture> TRACKER =
            new FixtureKey<>(
                    "satchelmobtracker:mob_tracker_fixture",
                    TrackerFixture.class
            );

    public static final JigKey<MobJig> JIG =
            new JigKey<>(
                    "satchelmobtracker:mob_tracker_jig",
                    MobJig.class
            );

    private static final Map<ServerLevel, Set<UUID>> INTERESTS = new ConcurrentHashMap<>();

    /**
     * Registers manual interest in {@code uuid} within {@code level}, for hand-driven
     * verification of the done bar's checklist.
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

    /**
     * Read-only snapshot of currently-watched UUIDs per level, for {@code /satchel mobtrack
     * list} -- a shallow copy ({@link Map#copyOf}) so a caller iterating it can't observe a
     * concurrent {@link #watch}/{@link #unwatch} mutating {@code INTERESTS} mid-iteration.
     */
    public static Map<ServerLevel, Set<UUID>> currentInterests() {
        return Map.copyOf(INTERESTS);
    }

    public static void init() {
        OUT.info("Satchel Mob Tracking Initializing (RM_SAT_021 verification aid)");

        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<MobScope, SatchelBundle>(
                        BUNDLE,
                        (MobScope scope) -> new SatchelBundle(scope, BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<MobScope> bundles =
                new JigBundles.Schema<>(
                        List.of(trackingBundle)
                );

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, MobTrackingModule::onScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, MobTrackingModule::onScopeUnloaded)
                        .on(ScopeEvent.Tick.class, MobTrackingModule::onScopeTick)
                        .build();

        MobJigConfig config = new MobJigConfig(JIG);

        // MobJigConfig ships no default sideApplicability by design -- this call is the exact
        // one the null-check exists to force. Boss (RM_FRO_018) will make the same call, also
        // SERVER, since defeat-detection is server-only today.
        config.binding().sideApplicability(JigPolicies.SideApplicability.SERVER);

        config.bundles().schema(bundles);

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        MobInterestRegistry.register(JIG, () -> INTERESTS);

        Satchel.registerJigConfig(config);
    }

    // ─────────────────────────────────────────────
    // Lifecycle handlers
    // ─────────────────────────────────────────────

    private static void onScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        // Shared-bus caveat, same reasoning as TrackingModule/PlayerTrackingModule's own
        // handlers: ScopeEvent.Loaded fires for every jig scoped to whatever just loaded, not
        // just this one.
        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;

        var bundle = jig.getOrCreate(info.scope(), BUNDLE);
        var tracker = bundle.getOrCreateFixture(TRACKER, TrackerFixture::new);
        tracker.countLoaded();

        MobScope scope = (MobScope) info.scope();
        OUT.info(
                "[MobTracking] LOADED mob=" + scope.mob().getType()
                        + " scope=" + scope.debugName()
                        + " dim=" + scope.mob().level().dimension().location()
        );
    }

    private static void onScopeUnloaded(ScopeEvent.Unloaded event) {
        ScopeInfo info = event.info();

        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        MobScope scope = (MobScope) info.scope();

        jig.ask(info.scope(), BUNDLE)
                .flatMap(b -> b.get(TRACKER))
                .ifPresent(TrackerFixture::countUnloaded);

        tickCounters.remove(scope.uuid());

        OUT.info(
                "[MobTracking] UNLOADED scope=" + scope.debugName()
        );
    }

    // Per-scope tick counter, keyed by scope UUID, purely for throttling this debug tool's own
    // log output -- not part of TrackerFixture's persisted state, doesn't need to survive a
    // restart, and clears itself on unload above so it can't leak across a scope's lifetime.
    private static final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private static final int LOG_EVERY_N_TICKS = 100; // ~5s at 20 TPS

    private static void onScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();

        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        MobScope scope = (MobScope) info.scope();

        jig.ask(info.scope(), BUNDLE)
                .flatMap(b -> b.get(TRACKER))
                .ifPresent(f -> {
                    f.countExternalTick();

                    // Throttled so a real per-tick log line doesn't spam the console.
                    int count = tickCounters.merge(scope.uuid(), 1, Integer::sum);
                    if (count % LOG_EVERY_N_TICKS == 0) {
                        OUT.info(
                                "[MobTracking] TICK mob=" + scope.mob().getType()
                                        + " scope=" + scope.debugName()
                        );
                    }
                });
    }
}
