package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.satchel.common.lifecycle.LivingChangeTargetSignal;
import com.arryn.satchel.common.lifecycle.LivingConversionSignal;
import net.minecraft.world.entity.LivingEntity;

/**
 * The two rules that keep a live Keeper what it is (FRO_215, SAT_101): allies never taking a boss as a target, and a
 * boss never converting. Both signals are server-only and unscoped, so each handler checks its own relevance. The
 * damage tally the credit rule reads is {@code CreditTally}'s.
 */
final class KeeperCombatHandlers {

    private KeeperCombatHandlers() {
    }

    /**
     * An ally -- an iron golem or a tamed wolf -- never takes a live boss as its target, even when its
     * owner is fighting it (boss.md § Allies never attack a Keeper). Cancelling keeps the ally's
     * current target, so this covers a golem's own scan, being hit, and a wolf following its owner.
     */
    static void onChangeTarget(LivingChangeTargetSignal signal) {
        LivingEntity target = signal.newTarget();
        if (target != null && DifficultyAPI.isAlly(signal.entity()) && BossMarkers.isLiveBoss(target)) {
            signal.cancel();
        }
    }

    /**
     * A live Keeper never converts (owner's ruling, SAT_101): a Zombie Keeper standing in water would
     * otherwise become a Drowned that is no longer tracked as the Keeper -- its record, size marker,
     * health bar and loot all keyed to the old entity. Cancelling keeps it the Keeper. Ordinary mobs
     * still convert. A conversion that skips Forge's Pre check (a Skeleton freezing into a Stray) is
     * not covered.
     */
    static void onConversion(LivingConversionSignal signal) {
        if (BossMarkers.isLiveBoss(signal.entity())) {
            signal.cancel();
        }
    }
}
