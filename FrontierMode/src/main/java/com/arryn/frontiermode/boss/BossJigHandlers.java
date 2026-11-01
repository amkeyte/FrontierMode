package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Event handlers for {@code BOSS_JIG}: tick, borders-loaded bootstrap, and defeat detection.
 */
final class BossJigHandlers {

    private BossJigHandlers() {
    }

    static void onTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BOSS_JIG.equals(info.jigKey())) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        // BOSS_JIG is BOTH, so this handler is invoked on a client too; the server-only work below has not
        // moved off it yet (boss nodes B to G), and its fixture mutations call Satchel.requireServer().
        BossBundle bundle = info.getOrCreate(FrontierKeys.BOSS_BUNDLE);
        BossFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS, BossFixture::new);

        SentinelJigHandlers.onBossTick(level, bundle);
        if (level instanceof ServerLevel serverLevel) {
            BossHealthBarHandlers.onBossTick(serverLevel, fixture);
            BossDefenseHandlers.onBossTick(serverLevel, bundle);
            BossLeashHandlers.onBossTick(serverLevel, fixture);
            // FRO_224: a scaled Keeper's path advances like a Sentinel's.
            BossAPI.roster(serverLevel).ifPresent(roster ->
                    roster.forEachPresent(serverLevel, present -> WidePathing.advanceIfOnWaypoint(present.boss())));
        }

        reconcilePathAgainstBossRecords(level);
    }

    /**
     * Handles {@code BORDERS_JIG}'s own {@code ScopeEvent.Loaded} -- registered on
     * {@code BOSS_JIG}'s config so this handler only ever installs server-side. Orchestrates the
     * level's initial Border+Boss bootstrap if it isn't seeded yet: Boss depends on Border, never the
     * reverse.
     */
    static void onBordersScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BORDERS_JIG.equals(info.jigKey())) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        if (!Satchel.isServer()) return;

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        var infoOpt = BorderAPI.INFO(level);
        if (infoOpt.isEmpty()) {
            OUT.warn("[Boss] onBordersScopeLoaded(): BordersFixture not resolvable for overworld"
                    + " level " + level.dimension().location() + " right after its own"
                    + " ScopeEvent.Loaded -- skipping the bootstrap check this cycle.");
            return;
        }

        if (infoOpt.get().seeded()) {
            return;
        }

        // The level's very first border has no natural center -- the one legitimate caller
        // of the deprecated no-center overload.
        @SuppressWarnings("deprecation")
        Result result = BorderAPI.grow(level);
        if (!result.isSuccess()) {
            OUT.warn("[Boss] onBordersScopeLoaded(): initial grow() failed for overworld level "
                    + level.dimension().location() + ": " + result.message());
            return;
        }

        KeeperAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
        BossTellFixture.createTellCurveIfAbsent(level, result.border().id());
        // RM_FRO_029 (Gloria): same paired-creation discipline as the Tell curve line above.
        BossSentinelsFixture.ensureSentinelCurves(level, result.border().id());
    }

    /**
     * Hands a tracked boss's death to its {@link Boss}. The death event stays the entry because credit needs
     * its damage source; the {@code Boss} settles the death once, asks its kind whether it is a defeat, and
     * runs the defeat or the loss (boss.md § What counts as a defeat).
     *
     * <p>A single physical death can fire more than one {@code LivingDeathEvent} (a Forge/vanilla
     * quirk -- multiple queued damage instances in the same tick). {@code markDefeated()} and
     * {@code dematerialize()} refuse the second; a duplicate death event is a clean no-op.
     */
    static void onMobDied(MobDied event) {
        Mob mob = (Mob) event.forgeEvent().getEntity();
        Level level = event.level();
        OUT.debug("[Boss] onMobDied: received for " + Ids.shortId(event.uuid()));

        Optional<Boss> bossOpt = BossAPI.roster(level).flatMap(roster -> roster.bossFor(mob.getUUID()));
        if (bossOpt.isEmpty()) {
            return;
        }

        // FRO_145: flag the client marker not-alive ahead of its release at MobLostInterest.
        BossAPI.publicMarkers(level).ifPresent(m -> m.markDead(mob.getUUID()));

        bossOpt.get().onDeath(mob);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static void reconcilePathAgainstBossRecords(Level level) {
        Optional<BordersPathFacet> pathOpt = BorderAPI.PATH(level);
        Optional<BordersCrudFacet> crudOpt = BorderAPI.CRUD(level);
        if (pathOpt.isEmpty() || crudOpt.isEmpty()) {
            return;
        }
        BordersPathFacet path = pathOpt.get();
        BordersCrudFacet crud = crudOpt.get();

        Map<Integer, UUID> pathLayers = new HashMap<>();
        for (UUID id : path.all()) {
            crud.get(id).ifPresent(b -> pathLayers.put(b.layer(), b.id()));
        }

        KeeperAPI.keepers(level).ifPresent(keepers -> keepers.reportMismatch(level, pathLayers));
    }
}
