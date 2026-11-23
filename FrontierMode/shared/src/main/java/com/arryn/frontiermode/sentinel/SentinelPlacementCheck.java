package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

/**
 * The placement gate of a natural spawn, before any mob exists: the Sentinel module's event for the loader's
 * placement-check event, on both lines (sentinels.md § The daylight rule). Its context is the level. Each line's
 * resolver passes only a firing {@link #passes}, so every other firing is dropped before an event is built and
 * vanilla's answer stands. {@link SentinelJigHandlers#onSpawnPlacementCheck} is its one consumer.
 *
 * <p>The event carries a result: {@link #allow()} forces the spawn through vanilla's whole monster predicate,
 * {@link #deny()} stops it, and neither leaves vanilla's answer. A deny is sticky. Valid only during dispatch.
 */
public interface SentinelPlacementCheck extends ModuleEvent<Level> {

    /**
     * The resolver's test, for a check that fires for every natural spawn attempt: a {@code MobCategory.MONSTER} type
     * spawning naturally that vanilla's own check has refused. The lift never applies to spawner blocks, eggs or
     * commands.
     */
    static boolean passes(EntityType<?> type, MobSpawnType spawnType, boolean vanillaAllows) {
        return type.getCategory() == MobCategory.MONSTER && spawnType == MobSpawnType.NATURAL && !vanillaAllows;
    }

    /** The level the spawn attempt is happening in. */
    default Level level() {
        return context();
    }

    /** The type being checked; no mob of it exists yet. */
    EntityType<?> entityType();

    /** Why the spawn is being attempted. */
    MobSpawnType spawnType();

    /** The candidate position the placement rules are checked against. */
    BlockPos pos();

    ModuleSignal.Verdict verdict();

    void allow();

    void deny();
}
