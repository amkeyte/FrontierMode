package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.player.PlayerJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerJig's slice of SatchelHealth -- see {@link SatchelHealth} for the shared
 * init/watch/pending-check machinery this registers into. Relocated from the single
 * SatchelHealth.java (SAT_060) once that file grew too large to keep editing as one piece --
 * same IDs, same behavior as before the split, not a rewrite.
 */
final class PlayerHealthCheck {

    private PlayerHealthCheck() {}

    public static final BundleKey<PlayerTrackerBundle> PLAYER_BUNDLE =
            new BundleKey<>(
                    "satcheltracker:player_tracker_bundle",
                    PlayerTrackerBundle.class
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

    static void register() {
        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        PLAYER_TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<PlayerScope, PlayerTrackerBundle>(
                        PLAYER_BUNDLE,
                        (PlayerScope scope) -> new PlayerTrackerBundle(scope, PLAYER_BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<PlayerScope> bundles =
                new JigBundles.Schema<>(List.of(trackingBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, PlayerHealthCheck::onPlayerScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, PlayerHealthCheck::onPlayerScopeUnloaded)
                        .on(ScopeEvent.Tick.class, PlayerHealthCheck::onPlayerScopeTick)
                        .build();

        // PlayerJigConfig already pins sideApplicability(SERVER) as its own default -- left
        // untouched here, deliberately: PlayerScope wraps a ServerPlayer directly and has no
        // client-side existence to widen into (see PlayerScope's own class docs), unlike
        // MobJig/LevelJig which both had a real CLIENT/BOTH gap to close.
        PlayerJigConfig config = new PlayerJigConfig(PLAYER_JIG);

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

        Satchel.registerJigConfig(config);
    }

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
        // Downgraded to .debug, same "silent on pass" reasoning as the MobJig handlers.
        OUT.debug(
                "[PlayerTracking] LOADED player=" + scope.player().getGameProfile().getName()
                        + " scope=" + scope.debugName()
                        + " dim=" + scope.player().serverLevel().dimension().location()
        );
    }

    /**
     * Same reasoning as {@link LevelHealthCheck}'s own check: {@code PlayerJig} teardown is
     * authoritative, not inferred -- {@code ServerForgeIngress#onPlayerScopeUnload} is the only
     * place that calls {@code tryRemoveSource(player)}, and it only runs from a real {@code
     * PlayerLoggedOutEvent}. The violation this check catches is teardown firing while the player
     * is still in the server's own online-player list -- exactly what would happen if a future
     * regression wired {@code tryRemoveSource} to something else that isn't a real logout (e.g.
     * {@code PlayerChangedDimensionEvent}, which {@code ServerForgeIngress}'s own comment already
     * flags as deliberately NOT hooked to this -- an easy mistake for someone to reintroduce later
     * without this check in place).
     *
     * <p><b>Deferred via {@link SatchelHealth#PENDING_HEALTH_CHECKS}</b>, same reasoning as {@link
     * LevelHealthCheck#onLevelScopeUnloaded}: a same-instant check would false-positive on every
     * ordinary logout, since {@code PlayerList#remove} fires {@code PlayerLoggedOutEvent} before
     * actually removing the player from the online list. Queuing the check for the next server
     * tick's execution pulse lets it observe the real post-removal state. On full shutdown, the
     * tick loop has already stopped before the remaining players get kicked, so the queue never
     * gets pumped again and the check is silently abandoned -- correct here too, for the same
     * reason as the level check.
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

        // Downgraded to .debug alongside the matching LOADED line above -- same reasoning.
        OUT.debug(
                "[PlayerTracking] UNLOADED player=" + scope.player().getGameProfile().getName()
                        + " scope=" + scope.debugName()
        );

        ServerPlayer player = scope.player();
        var server = player.getServer();

        if (server != null) {
            UUID playerUuid = player.getUUID();
            String playerName = player.getGameProfile().getName();

            SatchelHealth.PENDING_HEALTH_CHECKS.add(() -> {
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

                // Checkpoint, not routine noise -- fires once per logout, not per tick/per load.
                OUT.info(
                        "[SatchelHealth] PlayerJig teardown check passed: player=" + playerName
                );
            });
        }
    }

    // Per-scope tick counter, keyed by scope UUID, purely for throttling this debug tool's own
    // log output. Named distinctly from MobHealthCheck's own tickCounters -- separate classes now,
    // no longer sharing one file's namespace, but kept distinctly named anyway for clarity.
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
                        // Commented out for now (2026-08-29 playtest) -- same reasoning as the
                        // mob TICK log in MobHealthCheck; see that comment.
                        // OUT.debug(
                        //         "[PlayerTracking] TICK player=" + scope.player().getGameProfile().getName()
                        //                 + " scope=" + scope.debugName()
                        //                 + " dim=" + scope.player().serverLevel().dimension().location()
                        // );
                    }
                });
    }
}
