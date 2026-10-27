package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;

/**
 * The holder class for the difficulty module's ingress: one consumer of the Info report's
 * {@link InfoRequest}, which adds the {@code ladder:} line for a mob a rung has reached.
 * {@link DifficultyModule#init()} registers {@link #build()} through a lambda, so this class loads when the
 * server side boots.
 */
final class DifficultyIngress {

    private DifficultyIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.DIFFICULTY_INGRESS);
        ingress.listenForAll(InfoRequest.class, DifficultyIngress::onInfo);
        return ingress;
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
