package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Guardians defend their boss, on {@code BOSS_JIG}'s tick beside the health bar (guardian-mobs.md §
 * Defending the boss; FRO_197). Each tick, every present boss is checked for a new hit credited to a
 * player; on one, its guardians near the fight turn on that player. Then alerts whose window has
 * lapsed are let go.
 */
final class BossDefenseHandlers {

    private BossDefenseHandlers() {
    }

    static void onBossTick(ServerLevel level, BossBundle bundle) {
        Optional<BossFixture> bossOpt = bundle.boss();
        Optional<BossGuardiansFixture> guardiansOpt = bundle.guardians();
        if (bossOpt.isEmpty() || guardiansOpt.isEmpty()) {
            return;
        }
        BossGuardiansFixture guardians = guardiansOpt.get();

        PresentBosses.forEach(level, bossOpt.get(), present ->
                present.mobFixture().takeNewPlayerHit(present.boss())
                        .ifPresent(player ->
                                guardians.alertGuardiansOf(present.record().bossId(), present.boss(), player)));

        guardians.releaseLapsedAlerts(level);
    }
}
