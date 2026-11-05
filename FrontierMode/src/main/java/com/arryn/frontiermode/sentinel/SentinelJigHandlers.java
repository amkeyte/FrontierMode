package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.LivingConversionSignal;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;

/**
 * Event handlers for the Sentinel module's bare signals: the join that queues a Sentinel in presence, the daylight
 * gates, and the conversion cancel. A Sentinel is a record on the mob ({@link SentinelData}), and
 * {@link SentinelPresence} tracks the ones present in the level. Conversion itself is
 * {@link SentinelPresence#onMobSpawnFinalize}; the direct mob calls are {@link SentinelRules}'.
 *
 * <p>The handlers are installed on {@code SENTINEL_JIG}'s config only because it is the topically closest config;
 * the signals are bare and not scoped to it.
 */
final class SentinelJigHandlers {

    private SentinelJigHandlers() {
    }

    /**
     * A mob that carries the Sentinel record has joined the level, fresh or loaded from disk. It gets its
     * ally goal back (goals aren't saved, and the call is idempotent) and is queued in presence; the
     * Trinket's tick adopts it, or dismisses it when its boss is defeated or unknown.
     */
    static void onEntityJoinLevel(EntityJoinLevelSignal signal) {
        if (!(signal.level() instanceof ServerLevel level)
                || !(signal.entity() instanceof Mob mob)
                || !SentinelMarkers.isCandidate(mob)
                || !SentinelData.has(mob)) {
            return;
        }
        SentinelAllyTargetGoal.addTo(mob);
        SentinelPresence.of(level).ifPresent(presence -> presence.join(mob, !signal.loadedFromDisk()));
    }

    /**
     * A live Sentinel never converts: a conversion would copy the record to the new mob only after that
     * mob has joined the level, so the converted mob would be a Sentinel on the server with no size, no
     * trail and no entry in the synced fixture. Cancelling keeps the Sentinel whole.
     */
    static void onConversion(LivingConversionSignal signal) {
        if (SentinelData.has(signal.entity())) {
            signal.cancel();
        }
    }

    /**
     * The daylight rule's placement gate (gate 1 of 2; see {@link #onSpawnPositionCheck}). Inside a boss's sentinel placement radius, and only there,
     * the darkness gate lifts for a spawn that will become a sentinel: the same placement roll
     * conversion uses runs here, before the mob exists, and on a hit the spawn is allowed and its
     * position recorded so conversion skips its own roll. Feral owns the Wilds' gate, so this
     * acts inside the Frontier only. Types that can't be sentinels (see
     * {@code SentinelConversion#canBeSentinel}) are left alone.
     */
    static void onSpawnPlacementCheck(SpawnPlacementCheckSignal signal) {
        EntityType<?> type = signal.entityType();
        // Natural spawns only: the lift never applies to spawner blocks, eggs, or commands.
        if (type.getCategory() != MobCategory.MONSTER
                || signal.spawnType() != MobSpawnType.NATURAL
                || signal.defaultResult()
                || !(signal.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = signal.pos();
        if (BorderAPI.frontierDistanceAt(level, pos) > 0) {
            return;
        }
        SentinelPresence.of(level).ifPresent(presence -> {
            if (!SentinelConversion.canBeSentinel(type) || !presence.rollPlacementAt(level, pos)) {
                return;
            }
            // allow() bypasses all of Monster.checkMonsterSpawnRules, not just its darkness test, so
            // the one other rule -- a valid block to stand on -- is re-checked here.
            @SuppressWarnings("unchecked")
            EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) type;
            if (MobSpawns.surfaceAllows(level, mobType, signal.spawnType(), pos)) {
                signal.allow();
                presence.markLifted(pos);
            }
        });
    }

    /**
     * The daylight rule's position gate, the second gate a natural spawn passes: after the mob is
     * created and placed, vanilla checks {@code checkSpawnRules} (a light test for a {@code Monster})
     * and {@code checkSpawnObstruction}. A mob standing on a lifted position is allowed if nothing is
     * in its way; {@code allow()} bypasses the obstruction test too, so it's re-checked here. Reads the
     * lifted record and leaves it in place -- finalize-spawn consumes it.
     */
    static void onSpawnPositionCheck(SpawnPositionCheckSignal signal) {
        if (signal.spawnType() != MobSpawnType.NATURAL
                || !(signal.level() instanceof ServerLevel level)) {
            return;
        }
        Mob mob = signal.mob();
        SentinelPresence.of(level).ifPresent(presence -> {
            if (presence.isLifted(mob.blockPosition()) && MobSpawns.obstructionClear(level, mob)) {
                signal.allow();
            }
        });
    }
}
