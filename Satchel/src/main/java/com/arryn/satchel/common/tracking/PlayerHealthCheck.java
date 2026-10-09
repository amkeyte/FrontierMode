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

        PlayerJigConfig config = new PlayerJigConfig(PLAYER_JIG);

        // Was SERVER (PlayerJigConfig's own default, left untouched until SAT_063 built real
        // client-side PlayerJig existence). BOTH is what makes that path provable rather than
        // just declared -- same reasoning MobHealthCheck/LevelHealthCheck already give for their
        // own BOTH widening.
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);

        // Required for PERSISTENCE below to actually work, not optional -- ScopeEngine_Server
        // .resolveServerLevel() throws AccessFailed if info.referenceLevel() is null, and
        // PlayerJigConfig sets no default (unlike LevelJigConfig's LevelScope::level -- a
        // PlayerScope has no single Level it's intrinsically tied to, see PlayerJigConfig's own
        // doc). Only ever consulted server-side (ScopeEngine_Client never calls
        // resolveServerLevel), so the cast is safe regardless of this jig's own BOTH
        // sideApplicability above.
        config.binding().referenceLevel(
                scope -> ((ServerPlayer) scope.player()).serverLevel()
        );

        config.bundles().schema(bundles);

        // NETWORKING (SAT_063): SatchelNetwork.sendToPlayer() now gives PlayerScope-scoped
        // bundles a real distribution path, and JigConfigValidator's BOTH-applicability
        // requirement for allowsNetworking() is already satisfied by this jig's own binding
        // above. Without this, the client-side bundle would stay in CREATED forever -- see
        // ScopeEngine_Client.create()'s own doc (SAT_053): any declared capability means
        // "needs a parcel" client-side, and nothing sends one without this flag.
        config.policies().capabilities(new JigPolicies.Capabilities(true, true, false));
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
        // BOTH-applicability (SAT_063): scope.player() is a ServerPlayer server-side, a
        // LocalPlayer client-side -- branch rather than cast unconditionally, mirroring
        // LevelHealthCheck's own instanceof ServerLevel pattern. dimension() is only meaningful
        // via the server-side accessor (serverLevel()); the client-side line skips it rather
        // than reach for Minecraft.getInstance().level, which isn't this scope's own concern.
        if (scope.player() instanceof ServerPlayer serverPlayer) {
            OUT.debug(
                    "[PlayerTracking] LOADED player=" + serverPlayer.getGameProfile().getName()
                            + " scope=" + scope.debugName()
                            + " side=SERVER"
                            + " dim=" + serverPlayer.serverLevel().dimension().location()
            );
        } else {
            OUT.debug(
                    "[PlayerTracking] LOADED player=" + scope.player().getGameProfile().getName()
                            + " scope=" + scope.debugName()
                            + " side=CLIENT"
            );
        }
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
     * <p><b>Server-side only</b> (SAT_063 made this BOTH-applicability, but this specific check
     * stays server-only, same "known limitation" shape as {@link
     * LevelHealthCheck#onLevelScopeUnloaded}'s own check) -- there is no client-side equivalent
     * of "is this player still in the server's online-player list" to check; a client-side
     * teardown just tears down, uncontested, same as any ordinary scope unload elsewhere in
     * Satchel.
     *
     * <p><b>Deferred via {@link SatchelHealth#defer}</b>, same reasoning as {@link
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

        // Tracker fixture included in the log line, mirroring LevelHealthCheck's own UNLOADED
        // pattern -- internalTicks > 0 here is the direct, log-visible signal that this scope's
        // bundle actually reached ACTIVE and ticked (ScopeEngine_Client.onJigTick() gates on
        // ACTIVE), the thing SAT_063's networking wiring was specifically built to make happen
        // client-side.
        jig.ask(info.scope(), PLAYER_BUNDLE)
                .flatMap(b -> b.get(PLAYER_TRACKER))
                .ifPresent(f -> {
                    f.countUnloaded();
                    OUT.debug(
                            "[PlayerTracking] UNLOADED player=" + scope.player().getGameProfile().getName()
                                    + " scope=" + scope.debugName()
                                    + " " + f
                    );
                });

        // BOTH-applicability (SAT_063): the deferred violation check below only makes sense
        // server-side -- see this method's own doc. A client-side teardown skips it entirely.
        if (!(scope.player() instanceof ServerPlayer player)) {
            return;
        }

        var server = player.getServer();

        if (server != null) {
            UUID playerUuid = player.getUUID();
            String playerName = player.getGameProfile().getName();

            SatchelHealth.defer(() -> {
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

    private static void onPlayerScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();

        if (!PLAYER_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;
        PlayerScope scope = (PlayerScope) info.scope();

        jig.ask(info.scope(), PLAYER_BUNDLE)
                .flatMap(b -> b.get(PLAYER_TRACKER))
                .ifPresent(TrackerFixture::countExternalTick);
    }
}
