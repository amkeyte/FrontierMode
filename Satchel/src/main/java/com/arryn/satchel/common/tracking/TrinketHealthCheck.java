package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.LifecycleState;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;

import java.util.List;
import java.util.Optional;

/**
 * Trinket's slice of SatchelHealth (SAT_097) -- see {@link SatchelHealth}. A plain test jig of its
 * own, kept apart from every real jig so the check can't be satisfied by the code it guards: a
 * {@code LevelJig} holding one {@link Probe} Trinket and one persisted fixture.
 *
 * <p>
 * On a real scope the probe confirms, loudly (it throws, like every SatchelHealth check):
 * <ul>
 *   <li><b>attach</b> -- exactly once per scope load;</li>
 *   <li><b>both tick phases</b> -- strictly alternating {@code before}, {@code after}, and, while the
 *   probe bundle is ACTIVE, the fixture's own tick lands between them;</li>
 *   <li><b>detach</b> -- only after an attach, and its fixture write is counted.</li>
 * </ul>
 * The one thing a single run can't see is whether the detach write was <i>saved</i>: that is read
 * back on the next load. The probe bundle's {@code detaches} count is logged at INFO when the scope
 * loads; a value above zero after a restart confirms a write made in {@code onDetach} reached disk.
 * The probe shows in {@code /satchel trinkets}.
 */
final class TrinketHealthCheck {

    private TrinketHealthCheck() {}

    public static final BundleKey<TrinketProbeBundle> PROBE_BUNDLE =
            new BundleKey<>("satcheltracker:trinket_bundle", TrinketProbeBundle.class);

    public static final FixtureKey<TrinketProbeFixture> PROBE_FIXTURE =
            new FixtureKey<>("satcheltracker:trinket_fixture", TrinketProbeFixture.class);

    public static final JigKey<LevelJig> TRINKET_JIG =
            new JigKey<>("satcheltracker:trinket_jig", LevelJig.class);

    static void register() {
        var fixture = new JigBundles.FixtureDecl<TrinketProbeFixture>(
                PROBE_FIXTURE, TrinketProbeFixture::new, JigPolicies.CreatePolicy.ALWAYS);

        var bundle = new JigBundles.BundleDecl<LevelScope, TrinketProbeBundle>(
                PROBE_BUNDLE,
                (LevelScope scope) -> new TrinketProbeBundle(scope, PROBE_BUNDLE),
                List.of(fixture));

        EventHandlers eventHandlers = EventHandlers.builder()
                .on(ScopeEvent.Loaded.class, TrinketHealthCheck::onLoaded)
                .build();

        LevelJigConfig config = new LevelJigConfig(TRINKET_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(bundle)));
        config.bundles().trinket(Probe.class, Probe::new);

        // Persistence only: the check is about attach/tick/detach and a saved detach write, not
        // networking. Server-side, LevelJigConfig's default.
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

    private static void onLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        if (!TRINKET_JIG.equals(info.jigInfo().key)) return;

        var bundle = info.jigInfo().jig.getOrCreate(info.scope(), PROBE_BUNDLE);
        var fixture = bundle.getOrCreateFixture(PROBE_FIXTURE, TrinketProbeFixture::new);

        // Checkpoint: the saved-detach-write proof, read back across a restart.
        OUT.info("[SatchelHealth] Trinket probe loaded " + info.debugName()
                + ": detach writes persisted from earlier runs = " + fixture.detaches());
    }

    private static void violation(String message) {
        String full = "[SatchelHealth] VIOLATION: Trinket " + message;
        OUT.error(full);
        throw new IllegalStateException(full);
    }

    private static Optional<TrinketProbeFixture> fixtureOf(ScopeInfo info) {
        return info.ask(PROBE_BUNDLE).flatMap(b -> b.get(PROBE_FIXTURE));
    }

    private static boolean bundleActive(ScopeInfo info) {
        return info.ask(PROBE_BUNDLE)
                .map(b -> b.lifeCycleState() == LifecycleState.ACTIVE)
                .orElse(false);
    }

    /**
     * The probe itself. Public only because Satchel constructs it by type.
     */
    public static final class Probe implements Ticking {

        private int attaches;
        private long before;
        private long after;
        private long fixtureTicksAtBefore = -1;

        @Override
        public void onAttach(ScopeInfo info) {
            attaches++;
            if (attaches > 1) violation("probe attached " + attaches + " times to " + info.debugName());
        }

        @Override
        public void onTickBefore(ScopeInfo info) {
            if (attaches != 1) violation("probe ticked before attach on " + info.debugName());
            if (before != after) violation("probe onTickBefore ran twice without an onTickAfter on "
                    + info.debugName());
            before++;
            fixtureTicksAtBefore = bundleActive(info)
                    ? fixtureOf(info).map(TrinketProbeFixture::ticks).orElse(-1L)
                    : -1;
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            if (after + 1 != before) violation("probe onTickAfter ran without a matching onTickBefore on "
                    + info.debugName());
            after++;
            if (fixtureTicksAtBefore >= 0 && bundleActive(info)) {
                long now = fixtureOf(info).map(TrinketProbeFixture::ticks).orElse(-1L);
                if (now != fixtureTicksAtBefore + 1) {
                    violation("probe's tick phases did not bracket the fixtures' tick on "
                            + info.debugName() + ": fixture ticks " + fixtureTicksAtBefore
                            + " before, " + now + " after (expected exactly one in between)");
                }
            }
        }

        @Override
        public void onDetach(ScopeInfo info) {
            if (attaches != 1) violation("probe detached without a prior attach on " + info.debugName());
            // The write under test: made before the engine unloads the bundle, saved by its
            // terminal flush, read back as TrinketProbeFixture#detaches on the next load.
            fixtureOf(info).ifPresent(TrinketProbeFixture::countDetach);
            OUT.info("[SatchelHealth] Trinket probe detached from " + info.debugName()
                    + " after " + after + " tick(s)");
        }

        @Override
        public String describe() {
            return "attaches=" + attaches + " tickBefore=" + before + " tickAfter=" + after;
        }
    }
}
