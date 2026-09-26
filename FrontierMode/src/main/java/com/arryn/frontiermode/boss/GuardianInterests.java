package com.arryn.frontiermode.boss;

import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level interest for {@code GUARDIAN_MOB_JIG}: each level's set is the live, read-only view of
 * its {@code BossGuardiansFixture}'s record keys -- not a second, hand-maintained set. Deleting a
 * record stops tracking it. Registered per level from {@code BOSS_JIG}'s tick
 * ({@link GuardianJigHandlers#onBossTick}); nothing is rebuilt per call, since {@code MobDied}'s
 * gate walks every supplier on every mob death.
 */
final class GuardianInterests {

    static final Map<Level, Set<UUID>> MAP = new ConcurrentHashMap<>();

    private GuardianInterests() {
    }
}
