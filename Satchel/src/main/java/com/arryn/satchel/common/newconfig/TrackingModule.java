package com.arryn.satchel.common.newconfig;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.LevelJigConfig;
import com.arryn.satchel.common.util.out.OUT;

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
        // Build bundle schema -- RM_SAT_012: ScopeEngine.create()/.get() now read this
        // schema (bundleDecls) directly, so the separate BundleFactories.registerFactory(...)
        // call this module used to also make is gone. It was genuinely redundant, not a second
        // required registration -- see RM_SAT_012's roadmap node for the full history of that
        // duality. BorderModule.init() (FrontierMode) still does the old double-registration;
        // that's a follow-up for FrontierMode's own pass, not touched here.
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
        // LevelJigConfig already pins jigType/couplerType/scopeType/sourceType/
        // sideApplicability/scopeResolver/uuidDeterminer to the LevelJig defaults
        // this module needs -- only bundles and eventHandlers are per-module.

        LevelJigConfig config = new LevelJigConfig(JIG);

        config.bundles().schema(bundles);

        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

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

        // ScopeEvent is posted on the ONE shared per-side SatchelEventBus, not a per-jig bus --
        // every jig's onLoad() posts here, so this handler fires for every LevelJig scoped to
        // the same dimension (e.g. Border's), not just Tracking's own. info.jigInfo() correctly
        // reflects whichever jig actually posted THIS event; without this check we'd blindly use
        // that (possibly foreign) jig to fetch TrackingModule.BUNDLE, which its engine never
        // registered -- root cause of the RM_SAT_012 "No BundleDecl registered for
        // satcheltracker:tracker_bundle" crash (diagnosed 2026-08-15, see that node's log).
        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

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

        // Same shared-bus reasoning as onScopeLoaded above.
        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

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

        // Same shared-bus reasoning as onScopeLoaded above.
        if (!JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = info.jigInfo().jig;

        jig.ask(
                        info.scope(),
                        TrackingModule.BUNDLE
                )
                .flatMap(b -> b.get(TrackingModule.TRACKER))
                .ifPresent(TrackerFixture::countExternalTick);
    }

}
