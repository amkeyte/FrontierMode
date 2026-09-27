package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * Boss health bars on {@code BOSS_JIG}'s tick (boss.md § Health bar; FRO_189). Each tick, every
 * present boss reads its own kill credit and updates its bar; the bar and its viewers live on that
 * boss's {@link BossMobFixture}.
 */
final class BossHealthBarHandlers {

    private BossHealthBarHandlers() {
    }

    static void onBossTick(ServerLevel level, BossFixture fixture) {
        float minHealth = fixture.RULES.bossBarMinHealth();
        int lingerTicks = fixture.RULES.bossBarLingerTicks();

        for (BossRecord record : fixture.all()) {
            if (record.bossEntityId() == null
                    || !(level.getEntity(record.bossEntityId()) instanceof Mob boss)) {
                continue;
            }
            BossMobFixture.of(boss).ifPresent(mobFixture ->
                    mobFixture.tickHealthBar(level, boss, minHealth, lingerTicks));
        }
    }
}
