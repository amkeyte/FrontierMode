package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.difficulty.DifficultyAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * What makes a hostile that joined the Wilds Feral: the Feral record on the mob ({@link FeralData}) and
 * its place on the {@code feral} rung of the stat ladder. The decision to tag is {@link FeralModule}'s
 * join handler. There is no name, no scoreboard team and no marker: a Feral mob's only in-world sign is
 * the cosmetic tell.
 */
final class FeralTagging {

    private FeralTagging() {
    }

    /**
     * Tags a mob that has just joined {@code level}. A mob with no record gets one if it stands past the
     * Frontier ({@code distance} is that frontier distance, always positive). A mob that already carries
     * the record -- a pack member, whose record is written before it joins -- keeps it. Either way the
     * {@code feral} rung is applied: the first rung to reach a mob wins, so the call is idempotent, and a
     * pack member would otherwise never get one.
     *
     * <p>The rung's health and damage come from the path tip's position, not the distance
     * ({@link DifficultyAPI#applyFeralRung}).
     *
     * @return whether the mob is Feral, and so belongs in presence
     */
    static boolean tagFreshJoin(Mob mob, ServerLevel level) {
        if (!FeralData.has(mob)) {
            int frontierDistance = BorderAPI.frontierDistanceAt(level, mob.blockPosition());
            if (frontierDistance <= 0) {
                return false;
            }
            FeralData.writeIfAbsent(mob, frontierDistance, false);
        }
        DifficultyAPI.applyFeralRung(mob);
        return true;
    }
}
