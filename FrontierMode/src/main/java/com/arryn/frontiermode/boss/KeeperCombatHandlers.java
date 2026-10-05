package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.satchel.common.lifecycle.LivingChangeTargetSignal;
import com.arryn.satchel.common.lifecycle.LivingConversionSignal;
import com.arryn.satchel.common.lifecycle.LivingDamageSignal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The two combat rules that keep outside help from carrying a Keeper fight (FRO_215): the damage
 * tally the credit rule reads, and allies never taking a boss as a target. Both signals are
 * server-only and unscoped, so each handler checks its own relevance.
 */
final class KeeperCombatHandlers {

    private KeeperCombatHandlers() {
    }

    /**
     * Tallies the health a player actually removed from a boss, on its {@link BossMobFixture}
     * (boss.md § Player credit and non-player death). Counts damage whose causing entity is a player --
     * melee, arrows, tridents, TNT a player lit -- at {@code healthRemoved()}: after armor, capped at
     * the boss's remaining health. A pet's hit is not the player's own.
     */
    static void onLivingDamage(LivingDamageSignal signal) {
        if (!(signal.entity() instanceof Mob boss) || !(signal.source().getEntity() instanceof ServerPlayer)) {
            return;
        }
        BossMobFixture.of(boss).ifPresent(fixture -> fixture.addPlayerDamage(signal.healthRemoved()));
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
