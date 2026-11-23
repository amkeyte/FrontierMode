package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.LogicalSide;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.lifecycle.SatchelEventBus;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;

/**
 * A module event on a native event that carries a result (SAT_134 probe 3) -- see {@link SatchelHealth}. The probe has
 * the line post a synthetic result-carrying event of its own on the loader's bus, so it travels the real path (the
 * ingress's listener, the registration layer binding the event's result, the guards, the bus), and three consumers at
 * high, normal and low priority act on it. Afterwards the line reads the <i>native</i> event's result, which is what a
 * loader listener after Satchel would see:
 * <ul>
 *   <li><b>a deny is sticky</b> -- a deny followed by an allow, and an allow, a deny and an allow, both end DENY;</li>
 *   <li><b>an allow alone</b> ends ALLOW;</li>
 *   <li><b>an untouched event</b> keeps its default result;</li>
 *   <li><b>an event with no result</b> refuses {@code allow()} and {@code deny()}, rather than quietly doing
 *   nothing.</li>
 * </ul>
 * One line per side.
 *
 * <p>
 * The check is shared. A line whose loader has a result for every event implements {@link ResultSeen} on its synthetic
 * event, installs {@link ResultHealthCheck#consumers()}, and registers the check with its {@link Loader}; a line whose
 * loader has none registers no such probe.
 */
final class ResultHealthCheck {

    private ResultHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** Contributes the poster, which posts through {@code loader}. Called by a line that has this probe. */
    static void register(Loader loader) {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.BOTH, Order.LATE,
                ResultPoster.class, () -> new ResultPoster(loader));
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Result event " + message);
    }

    /** What the three consumers do to the event, by mode. */
    static final int DENY_THEN_ALLOW = 1;
    static final int ALLOW_ONLY = 2;
    static final int UNTOUCHED = 3;
    static final int ALLOW_DENY_ALLOW = 4;

    /** The line's posting of its synthetic result-carrying event. */
    interface Loader {

        /**
         * Builds a synthetic event carrying {@code mode}, posts it on the loader's bus on this thread, and answers its
         * native result before the post and after it.
         */
        Posted post(int mode);
    }

    /** The native event's result as it was built, and as the post left it. */
    record Posted(ModuleSignal.Verdict atStart, ModuleSignal.Verdict atEnd) {}

    // =================================================================
    // The events
    // =================================================================

    /** Unscoped, with a result. */
    interface ResultSeen extends ModuleEvent<Void> {

        /** The mode the synthetic event carries, copied when the module event was built. */
        int mode();

        ModuleSignal.Verdict verdict();

        void allow();

        void deny();
    }

    /** A module event whose native event carries no result. */
    static final class NoResult extends ModuleSignal<Object, Void> {
        NoResult() {
            super(new Object(), null);
        }
    }

    /** The global consumers a line's ingress installs. */
    static GlobalConsumers consumers() {
        GlobalConsumers consumers = new GlobalConsumers();

        consumers.listenForAll(ResultSeen.class, e -> {
            switch (e.mode()) {
                case DENY_THEN_ALLOW -> e.deny();
                case ALLOW_ONLY, ALLOW_DENY_ALLOW -> e.allow();
                default -> { }
            }
        }).priority(SatchelEventBus.Priority.HIGH);

        consumers.listenForAll(ResultSeen.class, e -> {
            switch (e.mode()) {
                case DENY_THEN_ALLOW -> e.allow();
                case ALLOW_DENY_ALLOW -> e.deny();
                default -> { }
            }
        });

        consumers.listenForAll(ResultSeen.class, e -> {
            if (e.mode() != ALLOW_DENY_ALLOW) return;
            ModuleSignal.Verdict before = e.verdict();
            e.allow();
            if (e.verdict() != before) {
                violation("an allow() after a deny changed the verdict from " + before + " to " + e.verdict());
            }
        }).priority(SatchelEventBus.Priority.LOW);

        return consumers;
    }

    // =================================================================
    // The probe
    // =================================================================

    /**
     * On the level host, both sides: posts the four modes once, from its tick on its own side's thread, and checks the
     * result each ends with.
     */
    public static final class ResultPoster implements Ticking {

        private final Loader loader;

        ResultPoster(Loader loader) {
            this.loader = loader;
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            LogicalSide side = ScopeInfoImpl.of(info).jigInfo().side();
            if (!SatchelHealth.firstTime("result:" + side)) return;

            expect(DENY_THEN_ALLOW, ModuleSignal.Verdict.DENY, "a deny followed by an allow", side);
            expect(ALLOW_ONLY, ModuleSignal.Verdict.ALLOW, "an allow alone", side);
            expect(UNTOUCHED, ModuleSignal.Verdict.DEFAULT, "an untouched event", side);
            expect(ALLOW_DENY_ALLOW, ModuleSignal.Verdict.DENY, "an allow, a deny and a later allow", side);

            NoResult plain = new NoResult();
            for (boolean allow : new boolean[]{true, false}) {
                boolean refused = false;
                try {
                    if (allow) plain.allow();
                    else plain.deny();
                } catch (IllegalStateException expected) {
                    refused = true;
                }
                if (!refused) {
                    violation("an event whose native event carries no result accepted " + (allow ? "allow()" : "deny()"));
                }
            }

            OUT.info("[SatchelHealth] Result-event probe: on the " + side
                    + " side a deny stayed denied after a later allow, an allow alone allowed, an untouched "
                    + "event kept its default result, and an event with no result refused allow() and deny()");
        }

        private void expect(int mode, ModuleSignal.Verdict expected, String what, LogicalSide side) {
            Posted posted = loader.post(mode);
            if (posted.atStart() != ModuleSignal.Verdict.DEFAULT) {
                violation("a new synthetic event did not start at DEFAULT");
            }
            if (posted.atEnd() != expected) {
                violation(what + " on the " + side + " side left the native event at " + posted.atEnd()
                        + ", expected " + expected);
            }
        }
    }
}
