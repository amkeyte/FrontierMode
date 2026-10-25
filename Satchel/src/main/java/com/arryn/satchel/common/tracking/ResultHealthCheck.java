package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.lifecycle.SatchelEventBus;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.LogicalSide;

/**
 * A module event on a {@code @HasResult} Forge event (SAT_134 probe 3) -- see {@link SatchelHealth}.
 * The probe posts a synthetic {@code @HasResult} event of its own on Forge's bus, so it travels the
 * real path (the ingress's Forge listener, the registration layer binding the event's result, the
 * guards, the bus), and three consumers at high, normal and low priority act on it. Afterwards it
 * reads the <i>native</i> event's result, which is what a Forge listener after Satchel would see:
 * <ul>
 *   <li><b>a deny is sticky</b> -- a deny followed by an allow, and an allow, a deny and an allow,
 *   both end DENY;</li>
 *   <li><b>an allow alone</b> ends ALLOW;</li>
 *   <li><b>an untouched event</b> keeps its default result;</li>
 *   <li><b>an event with no result</b> refuses {@code allow()} and {@code deny()}, rather than
 *   quietly doing nothing.</li>
 * </ul>
 * One line per side.
 */
final class ResultHealthCheck {

    private ResultHealthCheck() {}

    private static final String MODULE = "satchel_health";

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:result_events");

    static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.BOTH, () -> build());
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.BOTH, Order.LATE,
                ResultPoster.class, ResultPoster::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Result event " + message);
    }

    // =================================================================
    // The events
    // =================================================================

    /** What the three consumers do to the event, by mode. */
    private static final int DENY_THEN_ALLOW = 1;
    private static final int ALLOW_ONLY = 2;
    private static final int UNTOUCHED = 3;
    private static final int ALLOW_DENY_ALLOW = 4;

    /** The Forge event: carries a result, and the mode the consumers act on. */
    @Event.HasResult
    public static final class ProbeResultEvent extends Event {
        final int mode;

        ProbeResultEvent(int mode) {
            this.mode = mode;
        }
    }

    /** Unscoped, with a result. */
    static final class ResultSeen extends ModuleSignal<ProbeResultEvent, Void> {
        final int mode;   // fact, copied

        ResultSeen(ProbeResultEvent e) {
            super(e, null);
            mode = e.mode;
        }
    }

    /** A module event whose native event carries no result. */
    static final class NoResult extends ModuleSignal<Object, Void> {
        NoResult() {
            super(new Object(), null);
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);

        ingress.translate(ProbeResultEvent.class, ResultSeen::new);

        ingress.listenForAll(ResultSeen.class, e -> {
            switch (e.mode) {
                case DENY_THEN_ALLOW -> e.deny();
                case ALLOW_ONLY, ALLOW_DENY_ALLOW -> e.allow();
                default -> { }
            }
        }).priority(SatchelEventBus.Priority.HIGH);

        ingress.listenForAll(ResultSeen.class, e -> {
            switch (e.mode) {
                case DENY_THEN_ALLOW -> e.allow();
                case ALLOW_DENY_ALLOW -> e.deny();
                default -> { }
            }
        });

        ingress.listenForAll(ResultSeen.class, e -> {
            if (e.mode != ALLOW_DENY_ALLOW) return;
            ModuleSignal.Verdict before = e.verdict();
            e.allow();
            if (e.verdict() != before) {
                violation("an allow() after a deny changed the verdict from " + before + " to " + e.verdict());
            }
        }).priority(SatchelEventBus.Priority.LOW);

        return ingress;
    }

    // =================================================================
    // The probe
    // =================================================================

    /**
     * On the level host, both sides: posts the four modes once, from its tick on its own side's
     * thread, and checks the result each ends with.
     */
    public static final class ResultPoster implements Ticking {

        @Override
        public void onTickAfter(ScopeInfo info) {
            LogicalSide side = info.jigInfo().side();
            if (!SatchelHealth.firstTime("result:" + side)) return;

            expect(DENY_THEN_ALLOW, Event.Result.DENY, "a deny followed by an allow", side);
            expect(ALLOW_ONLY, Event.Result.ALLOW, "an allow alone", side);
            expect(UNTOUCHED, Event.Result.DEFAULT, "an untouched event", side);
            expect(ALLOW_DENY_ALLOW, Event.Result.DENY, "an allow, a deny and a later allow", side);

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

        private static void expect(int mode, Event.Result expected, String what, LogicalSide side) {
            ProbeResultEvent event = new ProbeResultEvent(mode);
            if (event.getResult() != Event.Result.DEFAULT) {
                violation("a new synthetic event did not start at DEFAULT");
            }
            MinecraftForge.EVENT_BUS.post(event);
            if (event.getResult() != expected) {
                violation(what + " on the " + side + " side left the native event at " + event.getResult()
                        + ", expected " + expected);
            }
        }
    }
}
