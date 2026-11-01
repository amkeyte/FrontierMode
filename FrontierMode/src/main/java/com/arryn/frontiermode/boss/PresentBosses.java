package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The loop every {@code BOSS_JIG} tick handler repeats: each boss record whose entity is present in
 * {@code level}, with its Keeper record, live {@link Mob} and {@link BossMobFixture}. A record with no
 * entity, an entity that isn't a {@code Mob}, no attached fixture, or no Keeper record (an entry that is
 * not whole waits) is skipped.
 */
final class PresentBosses {

    /** One present boss: its record, its Keeper record, live mob, and live-view fixture. */
    record Present(BossRecord record, KeeperRecord keeper, Mob boss, BossMobFixture mobFixture) {
    }

    private PresentBosses() {
    }

    static void forEach(ServerLevel level, BossFixture fixture, Consumer<Present> action) {
        Optional<KeeperFixture> keepersOpt = KeeperAPI.keepers(level);
        if (keepersOpt.isEmpty()) {
            return;
        }
        KeeperFixture keepers = keepersOpt.get();
        for (BossRecord record : fixture.all()) {
            if (record.bossEntityId() == null
                    || !(level.getEntity(record.bossEntityId()) instanceof Mob boss)) {
                continue;
            }
            Optional<KeeperRecord> keeperOpt = keepers.get(record.bossId());
            if (keeperOpt.isEmpty()) {
                continue;
            }
            BossMobFixture.of(boss).ifPresent(mobFixture ->
                    action.accept(new Present(record, keeperOpt.get(), boss, mobFixture)));
        }
    }
}
