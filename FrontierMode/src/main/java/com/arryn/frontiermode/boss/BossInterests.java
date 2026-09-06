package com.arryn.frontiermode.boss;

import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level interest set of currently-materialized boss entity UUIDs, shared between
 * {@link BossJigHandlers} and {@link BossModule#forceMaterialize}. Stale UUIDs (a since-defeated
 * boss) are never removed -- a UUID that no longer resolves is a cheap no-op for MobJig's
 * reconciliation poll.
 */
final class BossInterests {

    static final Map<Level, Set<UUID>> MAP = new ConcurrentHashMap<>();

    static void add(Level level, UUID entityId) {
        MAP.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(entityId);
    }

    private BossInterests() {
    }
}
