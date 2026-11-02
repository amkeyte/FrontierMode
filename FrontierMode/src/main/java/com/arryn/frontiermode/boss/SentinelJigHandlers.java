package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.core.WidePathing;
import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.boss.client.SentinelTrailClient;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.LivingConversionSignal;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Event handlers for the sentinel jigs: the join that queues a Sentinel in presence, the daylight
 * gates, the conversion cancel, and the client trail's tick. A Sentinel is a record on the mob
 * ({@link SentinelData}), and {@link BossSentinelsFixture} tracks the ones present in the level.
 * Conversion itself is {@link BossSentinelsFixture#onMobSpawnFinalize}; the direct mob/level calls are
 * {@link SentinelRules}'.
 *
 * <p>The bare-signal handlers ({@code EntityJoinLevelSignal}, the two spawn-check signals and
 * {@code LivingConversionSignal}) are installed on {@code BOSS_JIG}'s config only because it is the
 * topically closest config; they are not scoped to it.
 */
public final class SentinelJigHandlers {

    private SentinelJigHandlers() {
    }

    /**
     * {@code BOSS_JIG}'s tick: copies the trail's tuning into the public fixture (which pushes only when
     * a value differs), and nudges the wide Sentinels along their paths.
     */
    static void onBossTick(Level level, BossBundle bundle) {
        if (bundle.boss().isPresent()) {
            BossAPI.sentinelMarkers(level).ifPresent(pub -> pub.setTrail(
                    SentinelRules.trailIntervalTicks(), SentinelRules.trailLifetimeTicks(), SentinelRules.trailRadius()));
        }
        unstickWideSentinels(bundle);
    }

    /** FRO_224: advances a wide Sentinel's path when it stands on its next waypoint; see {@link WidePathing}. */
    private static void unstickWideSentinels(BossBundle bundle) {
        bundle.sentinels().ifPresent(g -> {
            for (Mob mob : g.presentSentinels()) {
                WidePathing.advanceIfOnWaypoint(mob);
            }
        });
    }

    /**
     * {@code SENTINEL_PUBLIC_JIG}'s tick, client side only: draws the trail. {@code ScopeEvent.Tick}
     * fires for every jig on the shared bus, so the jig key is checked. The drawing itself lives in a
     * client class that never loads on a dedicated server.
     */
    static void onSentinelPublicTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        if (!FrontierKeys.SENTINEL_PUBLIC_JIG.equals(info.jigKey())) return;
        Level level = ((LevelScope) info.scope()).level();
        if (!level.isClientSide) return;
        SentinelMarkers.clientFixture(level).ifPresent(fixture -> SentinelTrailClient.tick(level, fixture));
    }

    /**
     * A mob that carries the Sentinel record has joined the level, fresh or loaded from disk. It gets its
     * ally goal back (goals aren't saved, and the call is idempotent) and is queued in presence; the
     * fixture's tick adopts it, or dismisses it when its boss is defeated or unknown.
     */
    static void onEntityJoinLevel(EntityJoinLevelSignal signal) {
        if (!(signal.level() instanceof ServerLevel level)
                || !(signal.entity() instanceof Mob mob)
                || !SentinelMarkers.isCandidate(mob)
                || !SentinelData.has(mob)) {
            return;
        }
        SentinelAllyTargetGoal.addTo(mob);
        BossAPI.sentinelsQuiet(level).ifPresent(sentinels -> sentinels.join(mob, !signal.loadedFromDisk()));
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
     * {@code SentinelRules#canBeSentinel}) are left alone.
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
        BossAPI.sentinelsQuiet(level).ifPresent(sentinels -> {
            if (!SentinelRules.canBeSentinel(type) || !sentinels.rollPlacementAt(level, pos)) {
                return;
            }
            // allow() bypasses all of Monster.checkMonsterSpawnRules, not just its darkness test, so
            // the one other rule -- a valid block to stand on -- is re-checked here.
            @SuppressWarnings("unchecked")
            EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) type;
            if (MobSpawns.surfaceAllows(level, mobType, signal.spawnType(), pos)) {
                signal.allow();
                sentinels.markLifted(pos);
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
        BossAPI.sentinelsQuiet(level).ifPresent(sentinels -> {
            if (sentinels.isLifted(mob.blockPosition()) && MobSpawns.obstructionClear(level, mob)) {
                signal.allow();
            }
        });
    }

    /**
     * A boss was defeated (a player kill or a forced defeat): queues its present sentinels for the
     * staggered kill. {@code creditedPlayer} is null for a forced defeat. Called from both defeat paths
     * right after {@code markDefeated} returns true; a non-player boss death runs neither.
     */
    public static void onBossDefeated(Level level, UUID bossId, UUID creditedPlayer) {
        BossAPI.sentinelsQuiet(level).ifPresent(g -> g.doomSentinelsOf(bossId, creditedPlayer));
    }
}
