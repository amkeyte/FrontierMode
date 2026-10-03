package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.difficulty.DifficultyAPI;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * A Sentinel attacks an ally -- an iron golem or a tamed wolf -- it can see, ahead of anything else,
 * the defend-the-boss alert included (sentinels.md § Defending the boss). A nearest-attackable target
 * goal at the highest priority, so it takes over whenever an ally is in view and lets go when none is.
 * Goals aren't saved with a mob, so {@link #addTo} runs on every join of a Sentinel and is idempotent.
 */
final class SentinelAllyTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {

    // Vanilla's own interval for a player-targeting goal; line of sight required, reach not.
    private static final int CHECK_INTERVAL = 10;
    // Lower runs first. Vanilla's HurtByTargetGoal is 1 and the player-targeting goal 2.
    private static final int PRIORITY = 0;

    private SentinelAllyTargetGoal(Mob mob) {
        super(mob, LivingEntity.class, CHECK_INTERVAL, true, false, DifficultyAPI::isAlly);
    }

    /** Gives {@code sentinel} the goal unless it already has it. */
    static void addTo(Mob sentinel) {
        boolean present = sentinel.targetSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof SentinelAllyTargetGoal);
        if (!present) {
            sentinel.targetSelector.addGoal(PRIORITY, new SentinelAllyTargetGoal(sentinel));
        }
    }
}
