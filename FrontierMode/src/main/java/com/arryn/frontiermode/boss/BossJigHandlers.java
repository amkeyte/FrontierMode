package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

        SentinelJigHandlers.onBossTick(level, bundle);
        if (level instanceof ServerLevel serverLevel) {
            BossHealthBarHandlers.onBossTick(serverLevel, fixture);
            BossDefenseHandlers.onBossTick(serverLevel, bundle);
        }

        // FRO_108: fires any delayed force-flush a defeat cascade scheduled -- see
        // boss.md's "Cascade durability".
        fixture.pollForceFlush();

        fixture.SPAWN.finalizeUnpositioned(level);
        fixture.SPAWN.materializeUnresolved(level, uuid -> BossInterests.add(level, uuid));
        reconcilePathAgainstBossRecords(level, fixture);

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

        BossAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
        BossTellFixture.createTellCurveIfAbsent(level, result.border().id());
        // RM_FRO_029 (Gloria): same paired-creation discipline as the Tell curve line above.
        BossSentinelsFixture.ensureSentinelCurves(level, result.border().id());
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

        // FRO_145: flag the client marker not-alive ahead of its release at MobLostInterest.
        BossAPI.publicMarkers(level).ifPresent(m -> m.markDead(mob.getUUID()));

        // FRO_160: only a player's defeat advances the loop. Evaluated once, before markDefeated,
        // so a non-player death never marks the record defeated -- see boss.md's "Player credit
        // and non-player death".
        ServerPlayer credited = creditedPlayer(fixture, mob);
        if (credited == null) {
            onNonPlayerDeath(fixture, bossId.get(), mob);
            return;
        }

        if (!fixture.CRUD.markDefeated(bossId.get())) {
            return;
        }

        // The boss's sentinels die with it, staggered; see sentinels.md's "When the boss is defeated".
        SentinelJigHandlers.onBossDefeated(level, bossId.get(), credited.getUUID());

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
            defeatedRecord.ifPresent(record -> grantDefeatLoot(record, credited, mob, level));
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

        BossAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
        BossTellFixture.createTellCurveIfAbsent(level, result.border().id());
        // RM_FRO_029 (Gloria): same paired-creation discipline as the Tell curve line above.
        BossSentinelsFixture.ensureSentinelCurves(level, result.border().id());
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * FRO_160: the player credited with this death, or {@code null} for a non-player death.
     * Reads vanilla's own kill-credit state ({@code LivingEntity#getKillCredit()}): the player who
     * last damaged the mob, still set only while vanilla's 100-tick (5 s) window is open -- each
     * new player hit restarts it, a tamed pet's hit credits its online owner, and lava/drowning/
     * falls the player set in motion land inside the window. Needs no tracking of our own and no
     * new Forge subscription. The window is vanilla's fixed value, not a {@code Tunable}: the
     * remaining-time counter is protected, so a different window would need bespoke damage
     * tracking, which per boss.md arrives as a Satchel signal.
     */
    private static ServerPlayer creditedPlayer(BossFixture fixture, Mob mob) {
        if (!(mob.getKillCredit() instanceof ServerPlayer player)) {
            return null;
        }
        // FRO_215: and the players' own damage must add up to keeperPlayerShare of its maximum health;
        // allies, a raid, lava or another mob can finish a Keeper but not carry the fight. Counted
        // against maximum health, so a Keeper that healed simply needs more hits. If the tally is
        // somehow missing the credit stands rather than breaking the core loop.
        double share = fixture.RULES.keeperPlayerShare();
        boolean enough = BossMobFixture.of(mob)
                .map(f -> f.playerDamage() >= share * mob.getMaxHealth())
                .orElse(true);
        if (!enough) {
            OUT.info("[Boss] onMobDied: boss at " + mob.blockPosition()
                    + " died with a player credited, but players dealt under " + share
                    + " of its maximum health -- counted as a non-player death.");
            return null;
        }
        return player;
    }

    /**
     * FRO_160: a boss killed with no player credited returns to unmaterialized and respawns at its
     * recorded position through the ordinary materialization tick (after
     * {@code BossRules#respawnDelayTicks()}); no defeat, no cascade, no defeat loot -- the entity's
     * own vanilla drops are untouched. {@code dematerialize} returning false covers a duplicate
     * death event for the same death (already unmaterialized) and a record that vanished.
     */
    private static void onNonPlayerDeath(BossFixture fixture, UUID bossId, Mob mob) {
        if (!fixture.CRUD.dematerialize(bossId)) {
            return;
        }
        // The cleared bossEntityId is real state worth protecting: a crash before the next
        // autosave would restore a "materialized" record whose entity is dead.
        fixture.scheduleForceFlush();

        if (fixture.scheduleRespawn(bossId)) {
            OUT.info("[Boss] onMobDied: bossId=" + Ids.shortId(bossId) + " died with no player credit at "
                    + mob.blockPosition() + " -- respawning at its recorded position.");
        }
    }

    /**
     * The Keeper's defeat reward: vanilla-format loot tables rolled and dropped by {@link KeeperLoot}
     * (boss.md § Defeat loot; FRO_213). FRO_160: only reached for a player-credited defeat, so there is
     * always a credited player. FRO_134: a real item drop at the death position, never a direct inventory
     * insert, so it reads as a reward the player walks over and picks up.
     *
     * <p>Deliberately not called from {@code BossAPI.forceDefeat} (the {@code /boss defeat} admin path):
     * per the standing rule that the two cascade sites don't share code, and because an admin command
     * force-defeating bosses for loot is exactly the kind of riskless exploit that shouldn't exist.
     */
    private static void grantDefeatLoot(BossRecord record, ServerPlayer credited, Mob mob, Level level) {
        OUT.debug("[Boss] grantDefeatLoot: paying " + credited.getGameProfile().getName()
                + " for bossId=" + Ids.shortId(record.bossId()));
        if (level instanceof ServerLevel serverLevel) {
            KeeperLoot.pay(serverLevel, mob, record, credited);
        }
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
