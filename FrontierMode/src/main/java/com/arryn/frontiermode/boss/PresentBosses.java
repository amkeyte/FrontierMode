package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.function.Consumer;

/**
 * The loop every {@code BOSS_JIG} tick handler repeats: each boss record whose entity is present in
 * {@code level}, with its live {@link Mob} and {@link BossMobFixture}. A record with no entity, an
 * entity that isn't a {@code Mob}, or no attached fixture is skipped.
 */
final class PresentBosses {

    /** One present boss: its record, live mob, and live-view fixture. */
    record Present(BossRecord record, Mob boss, BossMobFixture mobFixture) {
    }

    private PresentBosses() {
    }

    static void forEach(ServerLevel level, BossFixture fixture, Consumer<Present> action) {
        for (BossRecord record : fixture.all()) {
            if (record.bossEntityId() == null
                    || !(level.getEntity(record.bossEntityId()) instanceof Mob boss)) {
                continue;
            }
            BossMobFixture.of(boss).ifPresent(mobFixture -> action.accept(new Present(record, boss, mobFixture)));
        }
    }
}
