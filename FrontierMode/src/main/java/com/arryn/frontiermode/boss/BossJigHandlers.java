package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Event handlers for {@code BOSS_JIG}: tick, borders-loaded bootstrap, and defeat detection. */
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

        if(!Satchel.isServer()) return;

        var jig = (LevelJig) info.jigInfo().jig;
        BossBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_BUNDLE);
        BossFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS, BossFixture::new);

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

        // On-path gate: a hand-placed off-path boss never triggers growth. An on-path boss only
        // triggers growth once it's the last living sibling on that border.
        Optional<UUID> borderId = fixture.CRUD.get(bossId.get()).flatMap(BossRecord::borderId);
        if (borderId.isEmpty()) {
            return;
        }
        if (fixture.INFO.anyAliveWithBorderId(borderId.get())) {
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
