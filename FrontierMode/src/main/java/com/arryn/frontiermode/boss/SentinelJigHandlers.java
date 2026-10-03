package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.boss.client.SentinelTrailClient;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.MobRemoved;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Event handlers for the sentinel jigs: the lifecycle that keeps {@link BossSentinelsFixture}'s
 * records and {@code SentinelPublicFixture}'s client-visible copy current. Conversion itself is
 * {@link BossSentinelsFixture#onMobSpawnFinalize}; the direct mob/level calls are
 * {@code DefaultBossRules}'. See sentinels.md's "Lifecycle through MobJig".
 *
 * <p>The bare-signal handlers ({@code EntityJoinLevelSignal}, {@code MobDied}) are installed on
 * {@code SENTINEL_MOB_JIG}'s config only because it is the topically closest config; they are not
 * scoped to it. The two {@code ScopeEvent} handlers check the jig key, since
 * {@code MobGainedInterest}/{@code MobLostInterest} post to one shared bus for every mob jig.
 */
final class SentinelJigHandlers {

    private SentinelJigHandlers() {
    }

    /**
     * {@code BOSS_JIG}'s tick: publishes the level's record-key view as {@code SENTINEL_MOB_JIG}'s
     * interest, and copies the trail's tuning into the public fixture (which pushes only when a value
     * differs).
     */
    static void onBossTick(Level level, BossBundle bundle) {
        bundle.sentinels().ifPresent(g -> SentinelInterests.MAP.put(level, g.recordIds()));
        bundle.boss().ifPresent(boss -> BossAPI.sentinelMarkers(level).ifPresent(pub -> pub.setTrail(
                boss.RULES.trailIntervalTicks(), boss.RULES.trailLifetimeTicks(), boss.RULES.trailRadius())));
    }

    /**
     * {@code SENTINEL_PUBLIC_JIG}'s tick, client side only: draws the trail. {@code ScopeEvent.Tick}
     * fires for every jig on the shared bus, so the jig key is checked. The drawing itself lives in a
     * client class that never loads on a dedicated server.
     */
    static void onSentinelPublicTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        if (!FrontierKeys.SENTINEL_PUBLIC_JIG.equals(info.jigInfo().key)) return;
        Level level = ((LevelScope) info.scope()).level();
        if (!level.isClientSide) return;
        SentinelMarkers.clientFixture(level).ifPresent(fixture -> SentinelTrailClient.tick(level, fixture));
    }

    /** A fresh join promotes a staged entry to a record, heals it to full, and publishes it to clients. */
    static void onEntityJoinLevel(EntityJoinLevelSignal signal) {
        if (signal.loadedFromDisk()
                || !(signal.level() instanceof ServerLevel level)
                || !(signal.entity() instanceof Mob mob)
                || !SentinelMarkers.isCandidate(mob)) {
            return;
        }
        BossAPI.sentinelsQuiet(level)
                .flatMap(sentinels -> sentinels.promote(mob.getUUID()))
                .ifPresent(record -> {
                    // Vanilla setup has run by now and can raise max health without healing.
                    BossAPI.bosses(level).ifPresent(boss -> boss.RULES.healSentinel(mob));
                    BossAPI.sentinelMarkers(level).ifPresent(pub -> pub.add(record.mobUuid(), record.scale()));
                    SentinelAllyTargetGoal.addTo(mob);
                });
    }

    /**
     * The daylight rule's placement gate (gate 1 of 2; see {@link #onSpawnPositionCheck}). Inside a boss's sentinel placement radius, and only there,
     * the darkness gate lifts for a spawn that will become a sentinel: the same placement roll
     * conversion uses runs here, before the mob exists, and on a hit the spawn is allowed and its
     * position recorded so conversion skips its own roll. Feral owns the Wilds' gate, so this
     * acts inside the Frontier only. Types that can't be sentinels (see
     * {@code BossRules#canBeSentinel}) are left alone.
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
            if (!sentinels.canBeSentinel(type) || !sentinels.rollPlacementAt(level, pos)) {
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
     * A boss was defeated (a player kill or a forced defeat): dooms its sentinels. {@code
     * creditedPlayer} is null for a forced defeat. Called from both defeat paths right after
     * {@code markDefeated} returns true; a non-player boss death runs neither.
     */
    static void onBossDefeated(Level level, UUID bossId, UUID creditedPlayer) {
        BossAPI.sentinelsQuiet(level).ifPresent(g -> g.doomSentinelsOf(bossId, creditedPlayer));
    }

    /**
     * Interest gained: the path a sentinel loaded back from disk takes. A doomed one is removed
     * first. Otherwise publishes it (idempotent),
     * and refreshes its dimensions -- loading an entity never recomputes its hitbox.
     */
    static void onMobGainedInterest(ScopeEvent.MobGainedInterest event) {
        if (!event.isJig(FrontierKeys.SENTINEL_MOB_JIG)) return;
        ScopeInfo info = event.info();
        Mob mob = ((MobScope) info.scope()).mob();
        Level level = mob.level();
        UUID uuid = mob.getUUID();

        BossAPI.sentinelsQuiet(level).flatMap(g -> g.record(uuid)).ifPresent(record -> {
            if (record.doomed()) {
                // Its boss was defeated while it was unloaded: removed on the spot with nothing, and
                // it never joins the public fixture, so it never shows a trail.
                BossAPI.bosses(level).ifPresent(boss -> boss.RULES.dismiss(mob));
                if (level instanceof ServerLevel serverLevel) {
                    deleteSentinel(serverLevel, uuid);
                }
                return;
            }
            BossAPI.sentinelMarkers(level).ifPresent(pub -> pub.add(uuid, record.scale()));
            mob.refreshDimensions();
            // Goals aren't saved: a reloaded Sentinel gets its ally goal back here, once the presence
            // poll confirms it (a fresh one already has it from its join; the call is idempotent).
            SentinelAllyTargetGoal.addTo(mob);
        });
    }

    /**
     * Interest lost: unloaded or gone, indistinguishable here. Retires the client copy; the server
     * record stays -- a sentinel unloaded with its chunk is still a sentinel when it loads back.
     * {@link #onMobRemoved} deletes the record of one that is gone for good.
     */
    static void onMobLostInterest(ScopeEvent.MobLostInterest event) {
        if (!event.isJig(FrontierKeys.SENTINEL_MOB_JIG)) return;
        MobScope scope = (MobScope) event.info().scope();
        UUID uuid = scope.uuid();
        OUT.debug("[Sentinel] onMobLostInterest(): " + Ids.shortId(uuid));
        BossAPI.sentinelMarkers(scope.mob().level()).ifPresent(pub -> pub.remove(uuid));
    }

    /**
     * {@code MobRemoved} is posted unscoped and only for an interest-registered mob: a sentinel that
     * left the level for good (killed, discarded by natural despawn or a vanilla conversion, or changed
     * dimension) loses its record. One that merely unloaded with its chunk keeps it -- it is still a
     * sentinel when it loads back. Records are per level, so a sentinel that changes dimension is an
     * ordinary mob in the new one.
     */
    static void onMobRemoved(MobRemoved event) {
        if (event.isGone() && event.level() instanceof ServerLevel level) {
            deleteSentinel(level, event.uuid());
        }
    }

    /** {@code MobDied} is posted unscoped: a no-op unless the dying mob has a sentinel record. */
    static void onMobDied(MobDied event) {
        if (event.level() instanceof ServerLevel level) {
            deleteSentinel(level, event.uuid());
        }
    }

    /**
     * Deletes {@code mobUuid}'s record and its client copy. A no-op for a mob with no record.
     */
    static void deleteSentinel(ServerLevel level, UUID mobUuid) {
        BossAPI.sentinelsQuiet(level).flatMap(g -> g.delete(mobUuid)).ifPresent(record -> {
            BossAPI.sentinelMarkers(level).ifPresent(pub -> pub.remove(mobUuid));
        });
    }
}
