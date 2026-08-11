package com.arryn.satchel.common.newconfig;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelResolver;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.level.LevelScopeCoupler;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.newnew.JigConfig;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;

public class TrackingModule {

    public static final BundleKey<SatchelBundle> BUNDLE =
            new BundleKey<>(
                    "satcheltracker:tracker_bundle",
                    SatchelBundle.class
            );

    public static final FixtureKey<TrackerFixture> TRACKER =
            new FixtureKey<>(
                    "satcheltracker:tracker_fixture",
                    TrackerFixture.class
            );

    public static final JigKey<LevelJig> JIG =
            new JigKey<>(
                    "satcheltracker:tracker_jig",
                    LevelJig.class
            );

    public static void init() {
        OUT.debug("Satchel Tracking Initializing");

        // ─────────────────────────────────────────────
        // Build bundle schema
        // ─────────────────────────────────────────────

        var trackerFixture =
                new JigBundles.FixtureDecl<TrackerFixture>(
                        TRACKER,
                        TrackerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var trackingBundle =
                new JigBundles.BundleDecl<LevelScope, SatchelBundle>(
                        BUNDLE,
                        (LevelScope scope) -> new SatchelBundle(scope, BUNDLE),
                        List.of(trackerFixture)
                );

        JigBundles.Schema<LevelScope> bundles =
                new JigBundles.Schema<>(
                        List.of(trackingBundle)
                );

        // ─────────────────────────────────────────────
        // Build eventHandlers handlers (replaces Strap)
        // ─────────────────────────────────────────────

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Loaded.class, TrackingModule::onScopeLoaded)
                        .on(ScopeEvent.Unloaded.class, TrackingModule::onScopeUnloaded)
                        .on(ScopeEvent.Tick.class, TrackingModule::onScopeTick)
                        .build();


        // ─────────────────────────────────────────────
        // Build JigConfig targeting LevelJig
        // ─────────────────────────────────────────────

        JigConfig<LevelScope, Level> config =
                JigConfig.<LevelScope, Level>builder()
                        .jigKey(JIG)
                        .jigType(LevelJig.class)
                        .scopeType(LevelScope.class)
                        .sourceType(Level.class)
                        .couplerType(LevelScopeCoupler.class) // we need to add this in
                        .resolveScope(LevelResolver::resolveScope)
                        .determineUUID(LevelResolver::determineUUID)
                        .bundles(bundles)

                        .sideApplicability(JigPolicies.SideApplicability.SERVER)

                        .lifecycle(JigPolicies.Lifecycle.defaults()
                                .withTick(true)) // assuming helper or explicit record

                        .eventHandlers(eventHandlers)

                        .build();

        // ─────────────────────────────────────────────
        // Register config (not a jig!)
        // ─────────────────────────────────────────────

        Satchel.registerJigConfig(config);
    }


    // ─────────────────────────────────────────────
    // Lifecycle handlers (unchanged logic)
    // ─────────────────────────────────────────────

    private static void onScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        var jig = info.jigInfo().jig;

        var bundle = jig.getOrCreate(
                info.scope(),
                TrackingModule.BUNDLE
        );

        var tracker = bundle.getOrCreateFixture(
                TrackingModule.TRACKER,
                TrackerFixture::new
        );

        tracker.countLoaded();

        OUT.debug(
                "[Tracking] LOADED "
                        + info.debugName()
                        + " " + tracker
        );
    }
    private static void onScopeUnloaded(ScopeEvent.Unloaded event) {
        ScopeInfo info = event.info();
        var jig = info.jigInfo().jig;

        jig.ask(
                        info.scope(),
                        TrackingModule.BUNDLE
                )
                .flatMap(b -> b.get(TrackingModule.TRACKER))
                .ifPresent(f -> {
                    f.countUnloaded();
                    OUT.debug(
                            "[Tracking] UNLOADED "
                                    + info.debugName()
                                    + " " + f
                    );
                });
    }
    private static void onScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        var jig = info.jigInfo().jig;

        jig.ask(
                        info.scope(),
                        TrackingModule.BUNDLE
                )
                .flatMap(b -> b.get(TrackingModule.TRACKER))
                .ifPresent(TrackerFixture::countExternalTick);
    }

}
