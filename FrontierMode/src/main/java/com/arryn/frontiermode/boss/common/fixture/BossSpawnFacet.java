package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
//import net.minecraft.server.level.ServerLevel;
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
            OUT.info("[Boss] finalizeUnpositioned(): boss " + record.bossId() + " (layer "
                    + record.layer() + ") position finalized at " + chosen.getX() + ", "
                    + chosen.getY() + ", " + chosen.getZ() + " -- home border " + border.id()
                    + ". Will materialize once that chunk is loaded.");
        }
    }

    /**
     * For each record that has a position but no live entity, waits for the chunk to be naturally
     * loaded (non-forcing), then materializes via {@link BossRulesFacet}. {@code onMaterialized}
     * receives the new entity's UUID so the caller can register mob interest.
     */
    public void materializeUnresolved(Level level, Consumer<UUID> onMaterialized) {
        for (BossRecord record : fixture.INFO.unmaterialized()) {
            BlockPos xz = record.position();
            if (!level.isLoaded(xz)) {
                continue;
            }
            fixture.RULES.materialize(level, xz, record.layer()).ifPresent(mob -> {
                fixture.CRUD.materialize(record.bossId(), mob.getUUID());
                onMaterialized.accept(mob.getUUID());
                if (MobScope.getFor(mob).isEmpty()) {
                    OUT.warn("[Boss] materializeUnresolved(): MobScope.getFor rejected a mob just"
                            + " spawned (bossId=" + record.bossId() + ") -- presence poll will"
                            + " still pick it up once ready.");
                }
            });
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
            OUT.warn("[Boss] forceMaterialize(): no record for bossId=" + bossId);
            return MaterializeOutcome.NO_RECORD;
        }
        BossRecord record = recordOpt.get();

        if (record.materialized()) {
            return MaterializeOutcome.ALREADY_MATERIALIZED;
        }

        if (!record.positioned()) {
            OUT.warn("[Boss] forceMaterialize(): bossId=" + bossId
                    + " has no finalized position yet (home border still pregenerating)"
                    + " -- cannot force-materialize.");
            return MaterializeOutcome.DECLINED;
        }

        BlockPos xz = record.position();
        OUT.info("[Boss] forceMaterialize(): forcing chunk load at " + xz
                + " for bossId=" + bossId);
        level.getChunk(xz);

        Optional<Mob> mobOpt = fixture.RULES.materialize(level, xz, record.layer());
        if (mobOpt.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): BossRules.materialize declined for bossId="
                    + bossId + " at " + xz + " (e.g. an all-liquid column).");
            return MaterializeOutcome.DECLINED;
        }

        Mob mob = mobOpt.get();
        fixture.CRUD.materialize(record.bossId(), mob.getUUID());
        onMaterialized.accept(mob.getUUID());
        OUT.info("[Boss] forceMaterialize(): spawned bossId=" + bossId
                + " entity=" + mob.getUUID() + " at " + mob.blockPosition());

        if (MobScope.getFor(mob).isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): MobScope.getFor rejected the just-spawned mob"
                    + " (bossId=" + bossId + ") -- presence poll will still pick it up.");
        }

        return MaterializeOutcome.SPAWNED;
    }

    // ── private helpers ───────────────────────────────────────────────────────

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
