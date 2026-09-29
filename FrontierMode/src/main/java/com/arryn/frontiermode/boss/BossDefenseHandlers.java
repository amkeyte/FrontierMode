package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

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

        for (BossRecord record : bossOpt.get().all()) {
            if (record.bossEntityId() == null
                    || !(level.getEntity(record.bossEntityId()) instanceof Mob boss)) {
                continue;
            }
            BossMobFixture.of(boss)
                    .flatMap(mobFixture -> mobFixture.takeNewPlayerHit(boss))
                    .ifPresent(player -> guardians.alertGuardiansOf(record.bossId(), boss, player));
        }

        guardians.releaseLapsedAlerts(level);
    }
}
