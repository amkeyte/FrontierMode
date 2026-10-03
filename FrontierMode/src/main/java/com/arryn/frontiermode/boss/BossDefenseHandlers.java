package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Sentinels defend their boss, on {@code BOSS_JIG}'s tick beside the health bar (sentinels.md §
 * Defending the boss; FRO_197). Each tick, every present boss is checked for a new hit credited to a
 * player; on one, its sentinels near the fight turn on that player. Then alerts whose window has
 * lapsed are let go.
 */
final class BossDefenseHandlers {

    private BossDefenseHandlers() {
    }

    static void onBossTick(ServerLevel level, BossBundle bundle) {
        Optional<BossFixture> bossOpt = bundle.boss();
        Optional<BossSentinelsFixture> sentinelsOpt = bundle.sentinels();
        if (bossOpt.isEmpty() || sentinelsOpt.isEmpty()) {
            return;
        }
        BossSentinelsFixture sentinels = sentinelsOpt.get();

        PresentBosses.forEach(level, bossOpt.get(), present ->
                present.mobFixture().takeNewPlayerHit(present.boss())
                        .ifPresent(player ->
                                sentinels.alertSentinelsOf(present.record().bossId(), present.boss(), player)));

        sentinels.releaseLapsedAlerts(level);
    }
}
