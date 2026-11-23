package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

/**
 * Forge's half of {@link ResultHealthCheck}: a synthetic {@code @HasResult} event of its own, its module event, and the
 * posting on Forge's bus that reads the native result back. The check is shared.
 */
public final class ResultIngress {

    private ResultIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:result_events");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.BOTH, () -> build());
        ResultHealthCheck.register(new ForgeLoader());
    }

    /** The Forge event: carries a result, and the mode the consumers act on. */
    @Event.HasResult
    public static final class ProbeResultEvent extends Event {
        final int mode;

        ProbeResultEvent(int mode) {
            this.mode = mode;
        }
    }

    static final class ForgeResultSeen extends ModuleSignal<ProbeResultEvent, Void>
            implements ResultHealthCheck.ResultSeen {
        private final int mode;   // fact, copied

        ForgeResultSeen(ProbeResultEvent e) {
            super(e, null);
            mode = e.mode;
        }

        @Override
        public int mode() {
            return mode;
        }
    }

    /** Posts the synthetic event on Forge's bus and reads its result before and after. */
    static final class ForgeLoader implements ResultHealthCheck.Loader {
        @Override
        public ResultHealthCheck.Posted post(int mode) {
            ProbeResultEvent event = new ProbeResultEvent(mode);
            ModuleSignal.Verdict atStart = verdictOf(event.getResult());
            MinecraftForge.EVENT_BUS.post(event);
            return new ResultHealthCheck.Posted(atStart, verdictOf(event.getResult()));
        }

        private static ModuleSignal.Verdict verdictOf(Event.Result result) {
            return switch (result) {
                case ALLOW -> ModuleSignal.Verdict.ALLOW;
                case DENY -> ModuleSignal.Verdict.DENY;
                case DEFAULT -> ModuleSignal.Verdict.DEFAULT;
            };
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        ingress.translate(ProbeResultEvent.class, ResultHealthCheck.ResultSeen.class, ForgeResultSeen::new);
        ingress.consumers(ResultHealthCheck.consumers());
        return ingress;
    }
}
