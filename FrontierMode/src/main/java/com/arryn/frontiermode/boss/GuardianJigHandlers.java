package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.boss.client.GuardianTrailClient;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Event handlers for the guardian jigs: the lifecycle that keeps {@link BossGuardiansFixture}'s
 * records and {@code GuardianPublicFixture}'s client-visible copy current. Conversion itself is
 * {@link BossGuardiansFixture#onMobSpawnFinalize}; the direct mob/level calls are
 * {@code DefaultBossRules}'. See guardian-mobs.md's "Lifecycle through MobJig".
 *
 * <p>The bare-signal handlers ({@code EntityJoinLevelSignal}, {@code MobDied}) are installed on
 * {@code GUARDIAN_MOB_JIG}'s config only because it is the topically closest config; they are not
 * scoped to it. The two {@code ScopeEvent} handlers check the jig key, since
 * {@code MobGainedInterest}/{@code MobLostInterest} post to one shared bus for every mob jig.
 */
final class GuardianJigHandlers {

    private GuardianJigHandlers() {
    }

    /**
     * {@code BOSS_JIG}'s tick: publishes the level's record-key view as {@code GUARDIAN_MOB_JIG}'s
     * interest, and copies the trail's tuning into the public fixture (which pushes only when a value
     * differs).
     */
    static void onBossTick(Level level, BossBundle bundle) {
        bundle.guardians().ifPresent(g -> GuardianInterests.MAP.put(level, g.recordIds()));
        bundle.boss().ifPresent(boss -> BossAPI.guardianMarkers(level).ifPresent(pub -> pub.setTrail(
                boss.RULES.trailIntervalTicks(), boss.RULES.trailLifetimeTicks(), boss.RULES.trailRadius())));
    }

    /**
     * {@code GUARDIAN_PUBLIC_JIG}'s tick, client side only: draws the trail. {@code ScopeEvent.Tick}
     * fires for every jig on the shared bus, so the jig key is checked. The drawing itself lives in a
     * client class that never loads on a dedicated server.
     */
    static void onGuardianPublicTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        if (!FrontierKeys.GUARDIAN_PUBLIC_JIG.equals(info.jigInfo().key)) return;
        Level level = ((LevelScope) info.scope()).level();
        if (!level.isClientSide) return;
        GuardianMarkers.clientFixture(level).ifPresent(fixture -> GuardianTrailClient.tick(level, fixture));
    }

    /** A fresh join promotes a staged entry to a record and publishes it to clients. */
    static void onEntityJoinLevel(EntityJoinLevelSignal signal) {
        if (signal.loadedFromDisk()
                || !(signal.level() instanceof ServerLevel level)
                || !(signal.entity() instanceof Mob mob)
                || !GuardianMarkers.isCandidate(mob)) {
            return;
        }
        BossAPI.guardiansQuiet(level)
                .flatMap(guardians -> guardians.promote(mob.getUUID()))
                .ifPresent(record -> BossAPI.guardianMarkers(level)
                        .ifPresent(pub -> pub.add(record.mobUuid(), record.scale())));
    }

    /**
     * The daylight rule's placement gate. Inside a boss's guardian placement radius, and only there,
     * the darkness gate lifts for a spawn that will become a guardian: the same placement roll
     * conversion uses runs here, before the mob exists, and on a hit the spawn is allowed and its
     * position recorded so conversion skips its own roll. Feral owns the Exterior's gate, so this
     * acts inside the Frontier only. Phantoms are never guardians, so their spawns are left alone.
     */
    static void onSpawnPlacementCheck(SpawnPlacementCheckSignal signal) {
        EntityType<?> type = signal.entityType();
        if (type.getCategory() != MobCategory.MONSTER
                || type == EntityType.PHANTOM
                || signal.defaultResult()
                || !(signal.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = signal.pos();
        if (BorderAPI.frontierDistanceAt(level, pos) > 0) {
            return;
        }
        BossAPI.guardiansQuiet(level).ifPresent(guardians -> {
            if (!guardians.rollPlacementAt(level, pos)) {
                return;
            }
            // allow() bypasses all of Monster.checkMonsterSpawnRules, not just its darkness test, so
            // the one other rule -- a valid block to stand on -- is re-checked here.
            @SuppressWarnings("unchecked")
            EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) type;
            if (MobSpawns.surfaceAllows(level, mobType, signal.spawnType(), pos)) {
                signal.allow();
                guardians.markLifted(pos);
            }
        });
    }

    /**
     * Interest gained: the path a guardian loaded back from disk takes. Publishes it (idempotent),
     * refreshes its dimensions -- loading an entity never recomputes its hitbox -- and applies the
     * glow toggle's current state.
     */
    static void onMobGainedInterest(ScopeEvent.MobGainedInterest event) {
        if (!event.isJig(FrontierKeys.GUARDIAN_MOB_JIG)) return;
        ScopeInfo info = event.info();
        Mob mob = ((MobScope) info.scope()).mob();
        Level level = mob.level();
        UUID uuid = mob.getUUID();

        BossAPI.guardiansQuiet(level).flatMap(g -> g.record(uuid)).ifPresent(record -> {
            BossAPI.guardianMarkers(level).ifPresent(pub -> pub.add(uuid, record.scale()));
            mob.refreshDimensions();
            BossAPI.bosses(level).ifPresent(boss -> boss.RULES.applyGlow(mob));
        });
    }

    /**
     * Interest lost: unloaded or gone, indistinguishable here. Retires the client copy; the server
     * record stays -- a guardian unloaded with its chunk is still a guardian when it loads back.
     * Deleting a record for a guardian that is gone for good waits on SAT_084.
     */
    static void onMobLostInterest(ScopeEvent.MobLostInterest event) {
        if (!event.isJig(FrontierKeys.GUARDIAN_MOB_JIG)) return;
        MobScope scope = (MobScope) event.info().scope();
        UUID uuid = scope.uuid();
        OUT.debug("[Guardian] onMobLostInterest(): " + Ids.shortId(uuid));
        BossAPI.guardianMarkers(scope.mob().level()).ifPresent(pub -> pub.remove(uuid));
    }

    /** {@code MobDied} is posted unscoped: a no-op unless the dying mob has a guardian record. */
    static void onMobDied(MobDied event) {
        if (event.level() instanceof ServerLevel level) {
            deleteGuardian(level, event.uuid());
        }
    }

    /**
     * Deletes {@code mobUuid}'s record, its guardian-team membership and its client copy. A no-op
     * for a mob with no record.
     */
    static void deleteGuardian(ServerLevel level, UUID mobUuid) {
        BossAPI.guardiansQuiet(level).flatMap(g -> g.delete(mobUuid)).ifPresent(record -> {
            BossAPI.bosses(level).ifPresent(boss -> boss.RULES.leaveGuardianTeam(mobUuid, level));
            BossAPI.guardianMarkers(level).ifPresent(pub -> pub.remove(mobUuid));
        });
    }
}
