package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.BorderModule;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.feral.FeralModule;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.MobSpawnFinalizeSignal;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Event handlers for {@code BOSS_JIG}: tick, borders-loaded bootstrap, defeat detection, and
 * (FRO_107) the mob-spawn-finalize fan-out -- registered on {@code BOSS_JIG}'s own config only
 * because that's where installation happens to occur (see {@link #onMobSpawnFinalize}'s own doc);
 * it isn't Boss-owned logic.
 */
final class BossJigHandlers {

    private BossJigHandlers() {
    }

    static void onTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BOSS_JIG.equals(info.jigInfo().key)) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        // FRO_104: the manual Satchel.isServer() check that used to live here was redundant --
        // BOSS_JIG is already declared SideApplicability.SERVER (BossModule.java), so this
        // handler is never invoked client-side to begin with; the branch could never fire.
        var jig = (LevelJig) info.jigInfo().jig;
        BossBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_BUNDLE);
        BossFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS, BossFixture::new);

        // FRO_108: fires any delayed force-flush a defeat cascade scheduled -- see
        // boss.md's "Cascade durability".
        fixture.pollForceFlush();

        fixture.SPAWN.finalizeUnpositioned(level);
        fixture.SPAWN.materializeUnresolved(level, uuid -> BossInterests.add(level, uuid));
        reconcilePathAgainstBossRecords(level, fixture);

    }

    /**
     * FRO_107: cross-module fan-out for {@link MobSpawnFinalizeSignal} (SAT_076) -- the same role
     * {@code FrontierMode.java}'s raw {@code @SubscribeEvent} played before this signal existed,
     * just off Satchel's own signal now instead of the raw Forge event. Not Boss-specific; Boss,
     * Border, and Feral each get a turn.
     *
     * <p>The Slime/MagmaCube deny check runs first and short-circuits, same ordering the original
     * handler had -- project owner ruling (2026-09-21, narrowed 2026-09-23 per FRO_154): Slime/
     * MagmaCube only removed from the Exterior, where Feral rules take over -- ordinary in-border
     * spawns are untouched, same standard vanilla rate as before this whole cluster existed.
     * {@link MobSpawnFinalizeSignal#denySpawn()}, not {@link MobSpawnFinalizeSignal#cancel()} --
     * see that method's own doc for the real Forge gotcha (canceling the signal/event alone does
     * not stop the entity from spawning).
     */
    static void onMobSpawnFinalize(MobSpawnFinalizeSignal signal) {
        // MobSpawnFinalizeSignal's own doc: server-only by construction (FinalizeSpawn "only
        // fired on the logical server") -- same defensive instanceof check the original
        // FrontierMode.java handler used, rather than trusting that guarantee with a raw cast.
        //noinspection resource
        if (!(signal.level() instanceof ServerLevel level)) {
            return;
        }

        // FRO_154: Exterior-only now -- same frontierDistanceAt(level, pos) > 0 check
        // FeralModule.onSpawnPlacementCheck/onEntityJoinLevel already use for "is this the
        // Exterior." An in-border Slime/MagmaCube falls through to Boss/Border/Feral like any
        // other mob (Feral's own onMobSpawnFinalize is already a no-op in-border anyway, via its
        // own frontierDistance <= 0 guard).
        if (signal.mob() instanceof Slime
                && BorderAPI.frontierDistanceAt(level, signal.mob().blockPosition()) > 0) {
            signal.denySpawn();
            return;
        }

        BossModule.onMobSpawnFinalize(signal.mob(), level);
        BorderModule.onMobSpawnFinalize(signal.mob(), level);
        FeralModule.onMobSpawnFinalize(signal.mob(), level);
    }

    /**
     * Handles {@code BORDERS_JIG}'s own {@code ScopeEvent.Loaded} -- registered on
     * {@code BOSS_JIG}'s config so this handler only ever installs server-side. Two
     * responsibilities on every fire, in order: rehydrates the {@link BossInterests} entry for
     * any boss already materialized before this server start (see the rehydration block's own
     * comment below), then orchestrates the level's initial Border+Boss bootstrap if it isn't
     * seeded yet: Boss depends on Border, never the reverse.
     */
    static void onBordersScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BORDERS_JIG.equals(info.jigInfo().key)) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        if (!Satchel.isServer()) return;

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        // Rehydrate Satchel's in-memory MobDied interest set for any boss already materialized
        // before this server start. BossInterests.MAP is plain in-memory state (see that class's
        // own doc) -- materializeUnresolved()/forceMaterialize() are its only other writers, and
        // neither one ever revisits a boss that already has a live entity, since both only ever
        // touch unmaterialized records. A boss loaded from disk with materialized()==true would
        // otherwise never get its real entity UUID added back in, and Satchel's MobDied gate
        // would silently drop its eventual death as "not interested" for the rest of this
        // server's life -- the defeat -> border-growth -> next-boss chain below would simply
        // never fire. Runs on every BORDERS_JIG-load cycle (this method's own trigger), not just
        // the not-yet-seeded bootstrap path below, since a previously-materialized boss can be
        // present whether or not this level still needs seeding.
        BossAPI.bosses(level).ifPresent(fixture -> {
            for (BossRecord record : fixture.all()) {
                if (record.materialized()) {
                    BossInterests.add(level, record.bossEntityId());
                }
            }
        });

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

        Optional<BossRecord> newBoss = BossAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
        BossTellFixture.createTellCurveIfAbsent(level, result.border().id());
        // RM_FRO_029 (Gloria): same paired-creation discipline as the Tell curve line above.
        BossGuardiansFixture.ensureGuardianCurves(level, result.border().id());
        newBoss.ifPresent(rec ->
                BossAPI.bossTells(level).ifPresent(tell -> tell.createRecord(rec.bossId())));
    }

    /**
     * Detects a tracked boss's defeat and closes the loop into Border: marks the record defeated,
     * grows a new border, and seeds the next boss. See boss.md's "Defeat detection and the
     * border-growth gap".
     *
     * <p>A single physical death can fire more than one {@code LivingDeathEvent} (a Forge/vanilla
     * quirk -- multiple queued damage instances in the same tick). {@code markDefeated()}'s return
     * value guards this; a duplicate death event for an already-defeated boss is a clean no-op.
     */
    static void onMobDied(MobDied event) {
        Mob mob = (Mob) event.forgeEvent().getEntity();
        Level level = event.level();
        OUT.debug("[Boss] onMobDied: received for " + Ids.shortId(event.uuid()));

        Optional<UUID> bossId = BossMobFixture.resolveBossId(mob);
        if (bossId.isEmpty()) {
            return;
        }

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] onMobDied(): BossFixture not available for level "
                    + level.dimension().location() + " -- can't mark bossId=" + bossId.get()
                    + " defeated.");
            return;
        }
        BossFixture fixture = fixtureOpt.get();

        if (!fixture.CRUD.markDefeated(bossId.get())) {
            return;
        }

        // FRO_108: the defeat mark itself is a real mutation worth protecting even when cascade
        // gating below stops growth right here -- see boss.md's "Cascade durability".
        fixture.scheduleForceFlush();

        // On-path gate: a hand-placed off-path boss never triggers growth. An on-path boss only
        // triggers growth once it's the last living sibling on that border.
        Optional<BossRecord> defeatedRecord = fixture.CRUD.get(bossId.get());
        Optional<UUID> borderId = defeatedRecord.flatMap(BossRecord::borderId);
        boolean lastStanding = borderId.isEmpty() || !fixture.INFO.anyAliveWithBorderId(borderId.get());

        // FRO_112: placeholder defeat loot -- pays out at the same last-standing point growth
        // itself gates on (trivially true for an off-path record), independent of whether growth
        // actually fires afterward. See grantDefeatLoot's own doc.
        if (lastStanding) {
            defeatedRecord.ifPresent(record -> grantDefeatLoot(record, event, mob, level));
        }

        if (borderId.isEmpty()) {
            return;
        }
        if (!lastStanding) {
            return;
        }

        BlockPos deathLocation = mob.blockPosition();
        Result result = BorderAPI.grow(level, deathLocation);

        if (!result.isSuccess()) {
            // A boss that fell out of the world can die outside build-height range, making
            // grow(deathLocation) fail. Fall back to the boss's own recorded position, which is
            // always within build height by construction.
            BlockPos fallbackCenter = fixture.CRUD.get(bossId.get())
                    .map(BossRecord::position).orElse(null);
            if (fallbackCenter != null) {
                OUT.warn("[Boss] onMobDied(): grow(" + deathLocation + ") failed for bossId="
                        + bossId.get() + ": " + result.message()
                        + " -- retrying from recorded position " + fallbackCenter);
                result = BorderAPI.grow(level, fallbackCenter);
            }
        }

        if (!result.isSuccess()) {
            OUT.warn("[Boss] onMobDied(): grow(" + deathLocation + ") failed for bossId="
                    + bossId.get() + ": " + result.message() + " -- not calling createBoss.");
            return;
        }

        Optional<BossRecord> newBoss = BossAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
        BossTellFixture.createTellCurveIfAbsent(level, result.border().id());
        // RM_FRO_029 (Gloria): same paired-creation discipline as the Tell curve line above.
        BossGuardiansFixture.ensureGuardianCurves(level, result.border().id());
        newBoss.ifPresent(rec ->
                BossAPI.bossTells(level).ifPresent(tell -> tell.createRecord(rec.bossId())));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * FRO_112: flat placeholder reward ahead of Boss Discovery's fuller, randomized,
     * difficulty-scaled loot table (Tier 2/3, still unclaimed) -- see boss.md's own "Placeholder
     * defeat loot" section. {@code record.layer() + 1} diamonds (0-indexed layer, so a layer-0
     * kill still pays out 1). If the death has no attacking player at all (mob-on-mob,
     * environmental, an admin's {@code /kill}), there's no one to reward -- skipped outright
     * rather than dropping loot with no player responsible for it, matching this ticket's own
     * "Lead Dev's call" on that edge case.
     *
     * <p>FRO_134 (project owner ask): a real item drop at the boss's death position, not a direct
     * inventory insert -- previously inserted straight into the killing player's inventory,
     * falling back to a drop only for whatever overflow didn't fit. Now always drops, same as any
     * other mob's own death loot, so it reads as a real reward the player has to walk over and
     * pick up rather than money silently appearing in their pockets.
     *
     * <p>Deliberately not called from {@code BossAPI.forceDefeat} (the {@code /boss defeat}
     * admin path) -- per the standing rule that the two cascade sites don't share code, and per
     * the same "no free loot without a real fight" reasoning FRO_110/111's own Feral drop
     * cancellation just settled: an admin command force-defeating bosses for diamonds is exactly
     * the kind of riskless exploit that shouldn't exist.
     */
    private static void grantDefeatLoot(BossRecord record, MobDied event, Mob mob, Level level) {
        if (!(event.forgeEvent().getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }

        int diamonds = record.layer() + 1;
        ItemStack stack = new ItemStack(Items.DIAMOND, diamonds);
        BlockPos deathLocation = mob.blockPosition();
        Containers.dropItemStack(level, deathLocation.getX() + 0.5, deathLocation.getY() + 0.5,
                deathLocation.getZ() + 0.5, stack);
    }

    private static void reconcilePathAgainstBossRecords(Level level, BossFixture fixture) {
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

        fixture.INFO.reportMismatch(level, pathLayers);
    }
}
