package com.arryn.satchel.common.newconfig;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.PlayerJigConfig;
import com.arryn.satchel.common.util.out.OUT;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RM_SAT_020 verification aid, mirroring {@link TrackingModule}'s already-established role for
 * {@code LevelJig} exactly: a real, minimal jig consumer whose whole job is proving the jig kind's
 * lifecycle actually fires, since {@code RM_FRO_006} (the first real {@code PlayerJig} consumer)
 * hasn't landed yet. Not a throwaway hack -- same permanent-diagnostic-infrastructure role
 * {@code TrackingModule} already plays for {@code LevelJig} (see the doc-coverage plan's
 * runtime-verification pass, which caught SAT_020/SAT_022/SAT_023 this same way).
 *
 * <p>
 * Logs at INFO (not DEBUG) specifically so the three FRO_023 checklist items for RM_SAT_020 are
 * readable straight out of {@code latest.log} with no debug-logging config needed:
 * <ul>
 *   <li><b>Login creates a scope:</b> exactly one {@code [PlayerTracking] LOADED} line per login,
 *   naming the player and their {@code PlayerScope} UUID.</li>
 *   <li><b>State survives a dimension change:</b> {@code [PlayerTracking] TICK} lines keep
 *   reporting the *same* scope UUID across a dimension change (logged dimension updates, scope
 *   identity doesn't) — a second {@code LOADED} line for the same player would mean the scope got
 *   torn down and rebuilt, which is exactly the bug this exists to catch.</li>
 *   <li><b>Logout tears the scope down:</b> exactly one {@code [PlayerTracking] UNLOADED} line per
 *   logout, and no further {@code TICK} lines for that player afterward.</li>
 * </ul>
 */
public class PlayerTrackingModule {

    public static final BundleKey<SatchelBundle> BUNDLE =
            new BundleKey<>(
                    "satcheltracker:player_tracker_bundle",
                    SatchelBundle.class
            );

    public static final FixtureKey<TrackerFixture> TRACKER =
            new FixtureKey<>(
                    "satcheltracker:player_tracker_fixture",
                    TrackerFixture.class
            );

    public static final JigKey<PlayerJig> JIG =
            new JigKey<>(
                    "satcheltracker:player_tracker_jig",
                    PlayerJig.class
            );

    public static void init() {
        OUT.info("Satchel Player Tracking Initializing (RM_SAT_020 verification aid)");

        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<PlayerScope, SatchelBundle>(
                        BUNDLE,
                        (PlayerScope scope) -> new SatchelBundle(scope, BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<PlayerScope> bundles =
                new JigBundles.Schema<>(
                        List.of(trackingBundle)
                );

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, PlayerTrackingModule::onScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, PlayerTrackingModule::onScopeUnloaded)
                        .on(ScopeEvent.Tick.class, PlayerTrackingModule::onScopeTick)
                        .build();

        // PlayerJigConfig already pins jigType/couplerType/scopeType/sourceType/
        // sideApplicability(SERVER)/scopeResolver/uuidDeterminer to the PlayerJig defaults this
        // module needs -- only bundles and eventHandlers are per-module, same division of labor
        // TrackingModule's LevelJigConfig usage already follows.
        PlayerJigConfig config = new PlayerJigConfig(JIG);

        config.bundles().schema(bundles);

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    // ─────────────────────────────────────────────
    // Lifecycle handlers
    // ─────────────────────────────────────────────

    private static void onScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        // Shared-bus caveat, same reasoning as TrackingModule's own handlers: ScopeEvent.Loaded
        // fires for every jig scoped to whatever just loaded, not just this one.
        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;

        var bundle = jig.getOrCreate(info.scope(), BUNDLE);
        var tracker = bundle.getOrCreateFixture(TRACKER, TrackerFixture::new);
        tracker.countLoaded();

        PlayerScope scope = (PlayerScope) info.scope();
        OUT.info(
                "[PlayerTracking] LOADED player=" + scope.player().getGameProfile().getName()
                        + " scope=" + scope.debugName()
                        + " dim=" + scope.player().serverLevel().dimension().location()
        );
    }

    private static void onScopeUnloaded(ScopeEvent.Unloaded event) {
        ScopeInfo info = event.info();

        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        PlayerScope scope = (PlayerScope) info.scope();

        jig.ask(info.scope(), BUNDLE)
                .flatMap(b -> b.get(TRACKER))
                .ifPresent(TrackerFixture::countUnloaded);

        tickCounters.remove(scope.uuid());

        OUT.info(
                "[PlayerTracking] UNLOADED player=" + scope.player().getGameProfile().getName()
                        + " scope=" + scope.debugName()
        );
    }

    // Per-scope tick counter, keyed by scope UUID, purely for throttling this debug tool's own
    // log output -- not part of TrackerFixture's persisted state, doesn't need to survive a
    // restart, and clears itself on unload below so it can't leak across a scope's lifetime.
    private static final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();
    private static final int LOG_EVERY_N_TICKS = 100; // ~5s at 20 TPS

    private static void onScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();

        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        PlayerScope scope = (PlayerScope) info.scope();

        jig.ask(info.scope(), BUNDLE)
                .flatMap(b -> b.get(TRACKER))
                .ifPresent(f -> {
                    f.countExternalTick();

                    // Throttled so a real per-tick log line doesn't spam the console -- enough
                    // resolution to catch a dimension change mid-session: watch the "dim=" field
                    // change between lines while "scope=" (the UUID) stays the same.
                    int count = tickCounters.merge(scope.uuid(), 1, Integer::sum);
                    if (count % LOG_EVERY_N_TICKS == 0) {
                        OUT.info(
                                "[PlayerTracking] TICK player=" + scope.player().getGameProfile().getName()
                                        + " scope=" + scope.debugName()
                                        + " dim=" + scope.player().serverLevel().dimension().location()
                        );
                    }
                });
    }
}
