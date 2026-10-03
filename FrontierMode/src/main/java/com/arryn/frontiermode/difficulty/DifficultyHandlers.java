package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.frontiermode.difficulty.common.LadderMath;
import com.arryn.frontiermode.difficulty.server.rules.DifficultyRules;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.LivingHurtSignal;
import com.arryn.satchel.common.util.MobData;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The ladder's two signal handlers (difficulty.md): the ordinary rung as a hostile joins the level,
 * and damage scaling as a hit lands. Both signals are server-only and unscoped, so each handler
 * checks its own relevance.
 */
final class DifficultyHandlers {

    private DifficultyHandlers() {
    }

    /**
     * A fresh join (not {@code loadedFromDisk}: a reloaded mob already has its values) of a ladder
     * hostile that no rung has reached takes the ordinary rung, if the ambient Difficulty at its own
     * position is 2 or more. Joining the level, not spawn-finalize, catches spawners, reinforcements,
     * raids and slime splits as well as natural spawns.
     */
    static void onEntityJoinLevel(EntityJoinLevelSignal signal) {
        if (signal.loadedFromDisk()
                || !(signal.entity() instanceof Mob mob)
                || !(signal.level() instanceof ServerLevel level)
                || !DifficultyAPI.isLadderHostile(mob)
                || Ladder.claimed(mob)) {
            return;
        }

        OptionalInt difficulty = ambientDifficulty(level, mob.blockPosition());
        if (difficulty.isEmpty() || difficulty.getAsInt() < 2) {
            return;
        }

        DifficultyRules rules = DifficultyRules.ACTIVE;
        int n = difficulty.getAsInt();
        if (Ladder.apply(mob, Ladder.RUNG_ORDINARY,
                LadderMath.multiplier(rules.ordinaryHealthGrowth(), n),
                LadderMath.multiplier(rules.ordinaryDamageGrowth(), n))) {
            OUT.debug("[Difficulty] ordinary rung n=" + n + " on " + mob.getType() + " at " + mob.blockPosition());
        }
    }

    /**
     * Damage isn't a stat: it is scaled here, as it lands, by the multiplier on whatever dealt it --
     * melee, arrow, blast or fangs alike (difficulty.md § Damage scales when it lands). The signal
     * fires before armor, so armor still reduces a scaled hit.
     */
    static void onLivingHurt(LivingHurtSignal signal) {
        CompoundTag data = MobData.readAttacker(signal.source(), Ladder.NAMESPACE);
        if (!Ladder.hasDamageMultiplier(data)) {
            return;
        }
        signal.setAmount((float) (signal.amount() * Ladder.damageMultiplier(data)));
    }

    /** The ambient Difficulty at {@code pos}; empty with no Relevant Border or before Border is ready. */
    private static OptionalInt ambientDifficulty(ServerLevel level, BlockPos pos) {
        Optional<List<Border>> containing = BorderAPI.RULES(level).map(rules -> rules.containing(pos));
        if (containing.isEmpty()) {
            return OptionalInt.empty();
        }
        return BorderRules.ACTIVE.ambientDifficultyAt(containing.get(), pos);
    }
}
