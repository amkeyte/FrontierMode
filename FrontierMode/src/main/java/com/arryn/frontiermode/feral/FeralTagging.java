package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;

/**
 * What makes a hostile that joined the Wilds Feral: the Feral record on the mob ({@link FeralData}) and
 * its place on the {@code feral} rung of the stat ladder, and the queue into {@link FeralPresence}. The
 * decision to tag is {@link #onHostileJoining}. There is no name, no scoreboard team and no marker: a
 * Feral mob's only in-world sign is the cosmetic tell.
 */
final class FeralTagging {

    private FeralTagging() {
    }

    /**
     * The consumer of {@link HostileJoining}: the actual tagging authority, and the presence queue
     * (feral.md § The spawn decision).
     *
     * <ul>
     *   <li><b>A mob loaded from disk</b> is never tagged again; if it carries the record it only
     *       re-queues for presence.</li>
     *   <li><b>A Slime</b> (which covers a Magma Cube) is never tagged: the split children a denied
     *       parent never produced, but a walked-in one can.</li>
     *   <li><b>Any other fresh hostile</b> gets the record if it has none and stands past the Frontier,
     *       and takes the {@code feral} rung either way, then queues for presence.</li>
     * </ul>
     *
     * <p>Tagging is here and not at spawn-finalize because the join fires however the mob was created
     * ({@code Level#addFreshEntity}): natural spawns, slime split children, density companions and pack
     * members, with one rule.
     */
    static void onHostileJoining(HostileJoining event) {
        Mob mob = event.mob();
        //noinspection resource
        if (!(event.level() instanceof ServerLevel level)) {
            return;
        }

        if (event.loadedFromDisk()) {
            if (FeralData.has(mob)) {
                OUT.debug("[Feral] reloaded " + EntityType.getKey(mob.getType()) + " at "
                        + mob.blockPosition().toShortString() + " queued for presence");
                FeralPresence.of(level).ifPresent(presence -> presence.join(mob, false));
            }
            return;
        }
        if (mob instanceof Slime) {
            return;
        }
        if (tag(mob, level)) {
            FeralPresence.of(level).ifPresent(presence -> presence.join(mob, true));
        }
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
    private static boolean tag(Mob mob, ServerLevel level) {
        boolean wrote = false;
        if (!FeralData.has(mob)) {
            int frontierDistance = BorderAPI.frontierDistanceAt(level, mob.blockPosition());
            if (frontierDistance <= 0) {
                return false;
            }
            wrote = FeralData.writeIfAbsent(mob, frontierDistance, false);
        }
        boolean rung = DifficultyAPI.applyFeralRung(mob);
        OUT.debug("[Feral] tagged " + EntityType.getKey(mob.getType()) + " at " + mob.blockPosition().toShortString()
                + (wrote ? ", record written" : ", record kept") + ", feral rung " + (rung ? "applied" : "not applied"));
        return true;
    }
}
