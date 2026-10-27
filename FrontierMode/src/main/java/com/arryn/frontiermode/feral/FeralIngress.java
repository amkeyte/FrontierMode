package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;

/**
 * The holder class for the feral module's ingress: one consumer of the Info report's {@link InfoRequest},
 * which adds the Feral record a mob carries. {@link FeralModule#init()} registers {@link #build()} through
 * a lambda, so this class loads when the server side boots.
 */
final class FeralIngress {

    private FeralIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.FERAL_INGRESS);
        ingress.listenForAll(InfoRequest.class, FeralIngress::onInfo);
        return ingress;
    }

    /** Registers the Feral answer; the work happens in the supplier, once per report. */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Feral] InfoRequest reached its consumer");

        for (Object subject : request.subjects()) {
            if (subject instanceof Mob mob) {
                request.add(subject, "feral", () -> feralLine(mob));
            }
        }
    }

    /** The distance the mob was tagged at and whether it is a pack member, or null for a mob with no record. */
    private static String feralLine(Mob mob) {
        return FeralData.read(mob)
                .map(facts -> "  Feral: distance " + facts.distance() + (facts.pack() ? ", pack member" : ""))
                .orElse(null);
    }
}
