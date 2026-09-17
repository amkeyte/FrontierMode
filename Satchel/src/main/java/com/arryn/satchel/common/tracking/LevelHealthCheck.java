package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;

/**
 * LevelJig's slice of SatchelHealth -- see {@link SatchelHealth} for the shared
 * init/watch/pending-check machinery this registers into. Relocated from the single
 * SatchelHealth.java (SAT_060) once that file grew too large to keep editing as one piece --
 * same IDs, same behavior as before the split, not a rewrite.
 */
final class LevelHealthCheck {

    private LevelHealthCheck() {}

    public static final BundleKey<LevelTrackerBundle> LEVEL_BUNDLE =
            new BundleKey<>(
                    "satcheltracker:tracker_bundle",
                    LevelTrackerBundle.class
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

    static void register() {
        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        LEVEL_TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<LevelScope, LevelTrackerBundle>(
                        LEVEL_BUNDLE,
                        (LevelScope scope) -> new LevelTrackerBundle(scope, LEVEL_BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<LevelScope> bundles =
                new JigBundles.Schema<>(List.of(trackingBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, LevelHealthCheck::onLevelScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, LevelHealthCheck::onLevelScopeUnloaded)
                        .on(ScopeEvent.Tick.class, LevelHealthCheck::onLevelScopeTick)
                        // MobJig's canary (SAT_060) piggybacks on this jig's own reliable tick --
                        // see MobHealthCheck.maintainCanary's own doc for why.
                        .on(ScopeEvent.Tick.class, MobHealthCheck::maintainCanary)
                        .build();

        LevelJigConfig config = new LevelJigConfig(LEVEL_JIG);

        // Was SERVER (TrackingModule's original call, LevelJigConfig's own default). BOTH lets a
        // server/client pair running only Satchel (no FrontierMode, no Border) still prove
        // LevelJig's client-side lifecycle path works, rather than depending on Border to be the
        // only thing that's ever exercised it.
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);

        config.bundles().schema(bundles);

        // NETWORKING: LevelScope-scoped send already works (SatchelNetwork.send(), see SAT_062's
        // enforcement of this exact flag) -- allowed here so scheduleSync()'s periodic parcel
        // resend actually exercises it, not just persistence/tick/lifecycle.
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
     * Unlike {@link MobHealthCheck}'s check, {@code LevelJig} teardown isn't <i>inferred</i> from
     * a poll -- {@code ServerForgeIngress#onLevelUnload} is the only place that calls {@code
     * tryRemoveSource(level)}, and it only runs from a real {@code LevelEvent.Unload}. So the
     * violation this check catches isn't "is the dimension actually gone" (trivially true given
     * how teardown is wired today) but "did teardown get triggered for a dimension that's still
     * the server's actively-registered instance for that key" -- exactly what a future regression
     * that calls {@code tryRemoveSource} from the wrong place (a stray extra hook, a
     * stale-reference bug like RM_SAT_014's) would produce. Server-side only -- see {@link
     * SatchelHealth}'s own docs for why there's no client-side equivalent yet.
     *
     * <p><b>Deferred via {@link SatchelHealth#PENDING_HEALTH_CHECKS}, not a direct check.</b>
     * {@code BlockableEventLoop#execute} runs its task INLINE when called from the thread that
     * already owns the loop, and this whole call chain ({@code MinecraftServer.stopServer()} ->
     * {@code LevelEvent.Unload} -> this handler) always runs on the server thread, so a
     * same-instant check would false-positive on every legitimate unload too. Queuing onto {@link
     * SatchelHealth#PENDING_HEALTH_CHECKS}, drained once per server tick from a genuinely later
     * call chain ({@code ServerForgeIngress#onExecutionPulse}, the next tick's execution pulse),
     * is what makes the check observe the real post-unload state. On final process shutdown
     * specifically, the tick loop has already stopped before {@code stopServer()} fires this
     * event, so the queue is never pumped again and the check is silently abandoned rather than
     * firing wrongly -- correct behavior, not a gap.
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

            SatchelHealth.PENDING_HEALTH_CHECKS.add(() -> {
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

                // Checkpoint, not routine noise -- fires once per level unload (in practice,
                // once per dimension on a clean `/stop`), not per tick/per load.
                OUT.info(
                        "[SatchelHealth] LevelJig teardown check passed: dimension="
                                + dimension.location()
                );
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
}
