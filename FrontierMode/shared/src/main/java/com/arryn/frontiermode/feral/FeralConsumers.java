package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;

/**
 * The feral module's global consumers (feral.md § How Feral hooks the game), written once for both lines: one for each of
 * the five events ({@link HostileJoining}, {@link WildsPlacementCheck}, {@link WildsPositionCheck}, {@link FeralDrops},
 * {@link FeralExperience}), and the consumer of the Info report's {@link InfoRequest}, which adds the Feral record a mob
 * carries. Each line's {@code FeralIngress} installs these.
 */
public final class FeralConsumers {

    private FeralConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(HostileJoining.class, FeralTagging::onHostileJoining);
        declared.listenForAll(WildsPlacementCheck.class, FeralSpawnGateHandlers::onPlacementCheck);
        declared.listenForAll(WildsPositionCheck.class, FeralSpawnGateHandlers::onPositionCheck);
        declared.listenForAll(FeralDrops.class, FeralLootHandlers::onFeralDrops);
        declared.listenForAll(FeralExperience.class, FeralLootHandlers::onFeralExperience);
        declared.listenForAll(InfoRequest.class, FeralConsumers::onInfo);
        return declared;
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
