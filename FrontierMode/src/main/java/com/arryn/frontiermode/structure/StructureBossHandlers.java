package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.frontiermode.structure.common.CairnPlacement;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * What the structure module does when Boss tells it something: a boss's position finalizing, heard
 * through {@link com.arryn.frontiermode.boss.BossPositionListeners} so Boss never imports this module.
 */
final class StructureBossHandlers {

    private StructureBossHandlers() {
    }

    /**
     * Record creation -- "the moment a boss's own position commits" (cairn.md). A boss gets
     * floor((pathPosition - 15) / 5) Cairns, all created at once.
     */
    static void onBossPositioned(Level level, BossRecord record) {
        // An entry without its Keeper record is not whole: it earns nothing yet.
        Optional<KeeperRecord> keeperOpt = KeeperAPI.keepers(level).flatMap(keepers -> keepers.get(record.bossId()));
        if (keeperOpt.isEmpty()) {
            return;
        }
        // The Keeper record's own path position, not KeeperAPI.pathPositionOf: that falls back to the layer,
        // which would hand an off-path boss Cairns it never earned.
        int count = CairnPlacement.count(keeperOpt.get().pathPosition());
        if (count == 0) {
            return;
        }

        StructureAPI.cairnFixture(level).ifPresent(fixture -> {
            int created = fixture.createForBoss(record.bossId(), record.position(), count).size();
            OUT.info("[Structure] boss " + Ids.shortId(record.bossId()) + " earns " + count
                    + " Cairn(s); " + created + " held for it.");
        });
    }
}
