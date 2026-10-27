package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * The holder class for the border module's ingress: one consumer of the Info report's {@link InfoRequest},
 * which adds the {@code frontier distance} line for an entity (0 = inside the Frontier).
 * {@link BorderModule#init()} registers {@link #build()} through a lambda, so this class loads when the
 * server side boots.
 */
final class BorderIngress {

    private BorderIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BORDER_INGRESS);
        ingress.listenForAll(InfoRequest.class, BorderIngress::onInfo);
        return ingress;
    }

    /** Registers the border answer; the work happens in the supplier, once per report. */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Border] InfoRequest reached its consumer");

        ServerLevel level = request.level();
        for (Object subject : request.subjects()) {
            if (subject instanceof Entity entity) {
                request.add(subject, "border",
                        () -> "  frontier distance " + BorderAPI.frontierDistanceAt(level, entity.blockPosition()));
            }
        }
    }
}
