package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Tells players a Keeper now waits in the frontier, once its Border has pregenerated and its position has
 * committed: a listener on {@link com.arryn.frontiermode.boss.BossPositionListeners}, registered in
 * {@link KeeperModule#init()}, so the base lifecycle announces nothing of its own.
 */
final class KeeperPositionListener {

    private KeeperPositionListener() {
    }

    /**
     * Says nothing about where (boss.md's client-secrecy policy). The first Keeper of a world -- none defeated
     * yet -- gets "An Amethyst Keeper waits..."; any later one gets "A new Amethyst Keeper waits...".
     */
    static void onBossPositioned(Level level, BossRecord record) {
        if (!KeeperKind.KEY.equals(record.kind()) || level.isClientSide) {
            return;
        }
        boolean first = BossAPI.bosses(level).map(fixture -> fixture.all().stream().allMatch(BossRecord::alive))
                .orElse(false);
        Component message = Component.translatable(first
                ? "message.frontiermode.keeper_waits_first"
                : "message.frontiermode.keeper_waits_next");
        for (var player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
