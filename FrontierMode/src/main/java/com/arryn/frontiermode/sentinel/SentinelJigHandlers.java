package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.core.MobSpawns;
import com.arryn.frontiermode.sentinel.common.SentinelRules;
import com.arryn.frontiermode.sentinel.common.goals.SentinelAllyTargetGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Consumers of the Sentinel module's events: the join that queues a Sentinel in presence, the daylight gates, and
 * the conversion cancel. A Sentinel is a record on the mob ({@link SentinelData}), and
 * {@link SentinelPresence} tracks the ones present in the level. Conversion itself is
 * {@link SentinelPresence#onMobSpawnFinalize}; the direct mob calls are {@link SentinelRules}'.
 *
 * <p>{@link SentinelIngress} registers them, and the filters that decide which firings reach them.
 */
final class SentinelJigHandlers {

    private SentinelJigHandlers() {
    }

    /**
     * A mob that carries the Sentinel record has joined the level, fresh or loaded from disk. It gets its
     * ally goal back (goals aren't saved, and the call is idempotent) and is queued in presence; the
     * Trinket's tick adopts it, or dismisses it when its boss is defeated or unknown.
     */
    static void onSentinelJoining(SentinelJoining joining) {
        Mob mob = joining.mob();
        SentinelAllyTargetGoal.addTo(mob);
        SentinelPresence.of(joining.level()).ifPresent(presence -> presence.join(mob, !joining.loadedFromDisk()));
    }

    /**
     * A live Sentinel never converts: a conversion would copy the record to the new mob only after that
     * mob has joined the level, so the converted mob would be a Sentinel on the server with no size, no
     * trail and no entry in the synced fixture. Cancelling keeps the Sentinel whole.
     */
    static void onSentinelConverting(SentinelConverting converting) {
        converting.cancel();
    }

    /**
     * The daylight rule's placement gate (gate 1 of 2; see {@link #onSpawnPositionCheck}). Inside a boss's sentinel placement radius, and only there,
     * the darkness gate lifts for a spawn that will become a sentinel: the same placement roll
     * conversion uses runs here, before the mob exists, and on a hit the spawn is allowed and its
     * position recorded so conversion skips its own roll. Feral owns the Wilds' gate, so this
     * acts inside the Frontier only. Types that can't be sentinels (see
     * {@code SentinelConversion#canBeSentinel}) are left alone.
     */
    static void onSpawnPlacementCheck(SentinelPlacementCheck check) {
        EntityType<?> type = check.entityType();
        Level level = check.level();
        BlockPos pos = check.pos();
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
            if (MobSpawns.surfaceAllows(level, mobType, check.spawnType(), pos)) {
                check.allow();
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
    static void onSpawnPositionCheck(SentinelPositionCheck check) {
        Level level = check.level();
        Mob mob = check.mob();
        SentinelPresence.of(level).ifPresent(presence -> {
            if (presence.isLifted(mob.blockPosition()) && MobSpawns.obstructionClear(level, mob)) {
                check.allow();
            }
        });
    }
}
