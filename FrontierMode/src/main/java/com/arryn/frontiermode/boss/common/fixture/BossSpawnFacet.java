package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.boss.BossPositionListeners;
import com.arryn.frontiermode.boss.common.BossPlatform;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Spawn-lifecycle facet for {@link BossFixture}: position finalization once pregeneration
 * completes, routine entity materialization, and forced materialization from admin commands.
 * Exposed as {@link BossFixture#SPAWN}.
 */
public final class BossSpawnFacet {

    private final BossFixture fixture;

    BossSpawnFacet(BossFixture fixture) {
        this.fixture = fixture;
    }

    /**
     * For each record without a finalized position, checks whether its home border's
     * pregeneration is ready and, if so, scores and commits a position. Runs before
     * {@link #materializeUnresolved} each tick so a border that finishes pregenerating this tick
     * doesn't lose an extra cycle.
     */
    public void finalizeUnpositioned(Level level) {
        for (BossRecord record : fixture.INFO.unpositioned()) {
            Optional<Border> borderOpt = resolveHomeBorder(level, record.layer());
            if (borderOpt.isEmpty()) {
                continue;
            }
            Border border = borderOpt.get();

            if (!BorderAPI.isPregenReady(level, border.id())) {
                continue;
            }

            BlockPos chosen = fixture.RULES.choosePosition(level, border);
            fixture.CRUD.finalizePosition(record.bossId(), chosen);
            OUT.info("[Boss] finalizeUnpositioned(): boss " + Ids.shortId(record.bossId()) + " (layer "
                    + record.layer() + ") position finalized at " + chosen.getX() + ", "
                    + chosen.getY() + ", " + chosen.getZ() + " -- home border " + Ids.shortId(border.id())
                    + ". Will materialize once that chunk is loaded.");

            // FRO_120 (Cairn): "the moment a qualifying boss's own position commits" is exactly
            // this point, per cairn.md's "Data model" section -- fire the listener registry with
            // the now-positioned record (fixture.CRUD.finalizePosition already committed it, so
            // this re-read reflects the real, final state) rather than the stale pre-finalize
            // copy still held in this loop's own `record` variable.
            fixture.CRUD.get(record.bossId()).ifPresent(updated ->
                    BossPositionListeners.fire(level, updated));
        }
    }

    /**
     * For each record that has a position but no live entity, waits for the chunk to be naturally
     * loaded (non-forcing), then materializes via {@link BossRulesFacet}. {@code onMaterialized}
     * receives the new entity's UUID so the caller can register mob interest.
     */
    public void materializeUnresolved(Level level, Consumer<UUID> onMaterialized) {
        for (BossRecord record : fixture.INFO.unmaterialized()) {
            // FRO_160: a boss returned here by a non-player death waits out its respawn delay (or
            // stays suspended at the repeat-death cap). Polled before the isLoaded check so the
            // delay counts real ticks.
            if (!fixture.respawnReady(record.bossId())) {
                continue;
            }
            BlockPos xz = record.position();
            if (!level.isLoaded(xz)) {
                continue;
            }
            // The entity spawns only once its platform is in place, so it never materializes over nothing.
            ensurePlatform(level, record);
            fixture.RULES.materialize(level, xz, record.layer()).ifPresent(mob -> {
                fixture.CRUD.materialize(record.bossId(), mob.getUUID());
                onMaterialized.accept(mob.getUUID());
                announceSpawn(level, record.layer());
                if (MobScope.getFor(mob).isEmpty()) {
                    OUT.warn("[Boss] materializeUnresolved(): MobScope.getFor rejected a mob just"
                            + " spawned (bossId=" + Ids.shortId(record.bossId()) + ") -- presence poll will"
                            + " still pick it up once ready.");
                }
            });
        }
    }

    /**
     * Chat-log broadcast for a real (tick-driven) materialization -- not the location itself,
     * just that one now exists, so this doesn't touch boss.md's client-secrecy policy on
     * position (see {@code BOSS_JIG}'s own "Module wiring" section). Deliberately not called
     * from {@link #forceMaterialize} -- that's an admin/debug action (a player command), and
     * broadcasting chat for every admin test-materialize would be noise, the same "no free
     * reward without a real trigger" reasoning already applied elsewhere in this codebase (e.g.
     * placeholder defeat loot skipping the {@code forceDefeat} admin path).
     */
    private static void announceSpawn(Level level, int layer) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Component message = Component.literal("[Boss] A new boss has emerged (Layer " + layer + ").");
        for (var player : serverLevel.players()) {
            player.sendSystemMessage(message);
        }
    }

    /**
     * Forces materialization of {@code bossId} regardless of chunk-loaded state. Loads the chunk
     * synchronously (safe: only ever called from a player command, never every tick), then
     * delegates through the same {@link BossRulesFacet#materialize} path as the tick-driven
     * routine. {@code onMaterialized} receives the new entity's UUID for interest registration.
     */
    public MaterializeOutcome forceMaterialize(Level level, UUID bossId,
            Consumer<UUID> onMaterialized) {
        Optional<BossRecord> recordOpt = fixture.CRUD.get(bossId);
        if (recordOpt.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): no record for bossId=" + Ids.shortId(bossId));
            return MaterializeOutcome.NO_RECORD;
        }
        BossRecord record = recordOpt.get();

        if (record.materialized()) {
            return MaterializeOutcome.ALREADY_MATERIALIZED;
        }

        if (!record.positioned()) {
            OUT.warn("[Boss] forceMaterialize(): bossId=" + Ids.shortId(bossId)
                    + " has no finalized position yet (home border still pregenerating)"
                    + " -- cannot force-materialize.");
            return MaterializeOutcome.DECLINED;
        }

        BlockPos xz = record.position();
        OUT.info("[Boss] forceMaterialize(): forcing chunk load at " + xz
                + " for bossId=" + Ids.shortId(bossId));
        level.getChunk(xz);
        ensurePlatform(level, record);

        Optional<Mob> mobOpt = fixture.RULES.materialize(level, xz, record.layer());
        if (mobOpt.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): BossRules.materialize declined for bossId="
                    + Ids.shortId(bossId) + " at " + xz + " (e.g. an all-liquid column).");
            return MaterializeOutcome.DECLINED;
        }

        Mob mob = mobOpt.get();
        fixture.CRUD.materialize(record.bossId(), mob.getUUID());
        // FRO_160: an admin spawn is the way back from a repeat-death suspension.
        fixture.clearRespawnState(record.bossId());
        onMaterialized.accept(mob.getUUID());
        OUT.info("[Boss] forceMaterialize(): spawned bossId=" + Ids.shortId(bossId)
                + " entity=" + Ids.shortId(mob.getUUID()) + " at " + mob.blockPosition());

        if (MobScope.getFor(mob).isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): MobScope.getFor rejected the just-spawned mob"
                    + " (bossId=" + Ids.shortId(bossId) + ") -- presence poll will still pick it up.");
        }

        return MaterializeOutcome.SPAWNED;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the record's spawn platform if it has none yet, sized to its layer's scale, and stores
     * its position. Built once per record: a respawned boss returns onto the platform already there
     * rather than re-carving it, which would delete anything a player built around the spot.
     * Bedrock isn't breakable in survival. The caller has confirmed the chunk is loaded.
     */
    private void ensurePlatform(Level level, BossRecord record) {
        if (record.platformBuilt()) {
            return;
        }
        BlockPos bossPos = record.position();
        BossPlatform.place(level, bossPos, fixture.RULES.scaleForLayer(record.layer()));
        fixture.CRUD.setPlatform(record.bossId(), bossPos.below());
        OUT.info("[Boss] ensurePlatform(): built bedrock platform for bossId=" + Ids.shortId(record.bossId())
                + " centered at " + bossPos.below());
    }

    private Optional<Border> resolveHomeBorder(Level level, int layer) {
        Optional<BordersPathFacet> pathOpt = BorderAPI.PATH(level);
        Optional<BordersCrudFacet> crudOpt = BorderAPI.CRUD(level);
        if (pathOpt.isEmpty() || crudOpt.isEmpty()) {
            return Optional.empty();
        }
        BordersPathFacet path = pathOpt.get();
        BordersCrudFacet crud = crudOpt.get();

        for (UUID id : path.all()) {
            Optional<Border> borderOpt = crud.get(id);
            if (borderOpt.isPresent() && borderOpt.get().layer() == layer) {
                return borderOpt;
            }
        }
        return Optional.empty();
    }
}
