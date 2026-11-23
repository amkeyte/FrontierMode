package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.Event;

/**
 * NeoForge's half of {@link ResultHealthCheck}: a synthetic event of its own that carries its own result type, as
 * NeoForge's events do, its module event, which binds that result in its constructor, and the posting on NeoForge's
 * bus that reads the native result back. The check is shared.
 */
public final class ResultIngress {

    private ResultIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:result_events");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.BOTH, () -> build());
        ResultHealthCheck.register(new ForgeLoader());
    }

    /** The NeoForge event: carries its own result, and the mode the consumers act on. */
    public static final class ProbeResultEvent extends Event {
        public enum Result { ALLOW, DEFAULT, DENY }

        final int mode;
        private Result result = Result.DEFAULT;

        ProbeResultEvent(int mode) {
            this.mode = mode;
        }

        public Result getResult() {
            return result;
        }

        public void setResult(Result result) {
            this.result = result;
        }
    }

    static final class ForgeResultSeen extends ModuleSignal<ProbeResultEvent, Void>
            implements ResultHealthCheck.ResultSeen {
        private final int mode;   // fact, copied

        ForgeResultSeen(ProbeResultEvent e) {
            super(e, null);
            mode = e.mode;
            bindResult(() -> verdictOf(e.getResult()), v -> e.setResult(resultOf(v)));
        }

        @Override
        public int mode() {
            return mode;
        }
    }

    /** Posts the synthetic event on NeoForge's bus and reads its result before and after. */
    static final class ForgeLoader implements ResultHealthCheck.Loader {
        @Override
        public ResultHealthCheck.Posted post(int mode) {
            ProbeResultEvent event = new ProbeResultEvent(mode);
            ModuleSignal.Verdict atStart = verdictOf(event.getResult());
            NeoForge.EVENT_BUS.post(event);
            return new ResultHealthCheck.Posted(atStart, verdictOf(event.getResult()));
        }
    }

    private static ModuleSignal.Verdict verdictOf(ProbeResultEvent.Result result) {
        return switch (result) {
            case ALLOW -> ModuleSignal.Verdict.ALLOW;
            case DENY -> ModuleSignal.Verdict.DENY;
            case DEFAULT -> ModuleSignal.Verdict.DEFAULT;
        };
    }

    private static ProbeResultEvent.Result resultOf(ModuleSignal.Verdict verdict) {
        return switch (verdict) {
            case ALLOW -> ProbeResultEvent.Result.ALLOW;
            case DENY -> ProbeResultEvent.Result.DENY;
            case DEFAULT -> ProbeResultEvent.Result.DEFAULT;
        };
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        ingress.translate(ProbeResultEvent.class, ResultHealthCheck.ResultSeen.class, ForgeResultSeen::new);
        ingress.consumers(ResultHealthCheck.consumers());
        return ingress;
    }
}
