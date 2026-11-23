package com.arryn.frontiermode.border.server;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The border module's global consumers, written once for both lines: the Info report's {@link InfoRequest}, which one
 * consumer answers with the {@code frontier distance} line for an entity (0 = inside the Frontier). The respawn is
 * scoped to the player's own scope and heard by {@code BorderPlayerTick}'s Trinket, so it has no global consumer. Each
 * line's {@code BorderIngress} installs these.
 */
public final class BorderConsumers {

    private BorderConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(InfoRequest.class, BorderConsumers::onInfo);
        return declared;
    }

    /** Registers the border answer; the work happens in the supplier, once per report. */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Border] InfoRequest reached its consumer");

        Level level = request.level();
        for (Object subject : request.subjects()) {
            if (subject instanceof Entity entity) {
                request.add(subject, "border",
                        () -> "  frontier distance " + BorderAPI.frontierDistanceAt(level, entity.blockPosition()));
            }
        }
    }
}
