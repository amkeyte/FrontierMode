package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;

/**
 * The difficulty module's global consumers, written once for both lines: one for each of the three events that carry
 * the stat ladder ({@link MobJoining}, {@link LadderHit}, {@link WolfTaming}), and the consumer of the Info report's
 * {@link InfoRequest}, which adds the {@code ladder:} line for a mob a rung has reached. Each line's
 * {@code DifficultyIngress} installs these.
 */
public final class DifficultyConsumers {

    private DifficultyConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(MobJoining.class, DifficultyHandlers::onMobJoining);
        declared.listenForAll(LadderHit.class, DifficultyHandlers::onLadderHit);
        declared.listenForAll(WolfTaming.class, DifficultyHandlers::onWolfTaming);
        declared.listenForAll(InfoRequest.class, DifficultyConsumers::onInfo);
        return declared;
    }

    /** Registers the ladder answer; the work happens in the supplier, once per report. */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Difficulty] InfoRequest reached its consumer");

        for (Object subject : request.subjects()) {
            if (subject instanceof Mob mob) {
                request.add(subject, "difficulty", () -> ladderLine(mob));
            }
        }
    }

    /** The rung, its level when there is one, and the two multipliers; null when no rung has reached the mob. */
    private static String ladderLine(Mob mob) {
        return DifficultyAPI.describe(mob).map(ladder -> "  ladder: " + ladder).orElse(null);
    }
}
