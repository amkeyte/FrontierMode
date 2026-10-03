package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import net.minecraft.server.level.ServerLevel;

/**
 * Boss health bars on {@code BOSS_JIG}'s tick (boss.md § Health bar; FRO_189, FRO_209). Each tick,
 * every present boss reads its own kill credit and updates its bar -- only a milestone Keeper has
 * one; the bar and its viewers live on that boss's {@link BossMobFixture}.
 */
final class BossHealthBarHandlers {

    private BossHealthBarHandlers() {
    }

    static void onBossTick(ServerLevel level, BossFixture fixture) {
        int lingerTicks = fixture.RULES.bossBarLingerTicks();

        PresentBosses.forEach(level, fixture, present ->
                present.mobFixture().tickHealthBar(level, present.boss(),
                        BossAPI.isMilestone(present.record()), lingerTicks));
    }
}
