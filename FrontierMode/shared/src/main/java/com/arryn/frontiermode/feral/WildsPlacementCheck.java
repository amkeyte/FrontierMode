package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

/**
 * The placement gate of a natural spawn, before any mob exists: the module's event for the loader's placement-check
 * event, on both lines (feral.md § How Feral hooks the game). Its context is the level. Each line's resolver passes only
 * a firing that {@link #passes}, so every other firing is dropped before an event is built and vanilla's own checks are
 * unchanged. {@link FeralSpawnGateHandlers#onPlacementCheck} is its one consumer.
 *
 * <p>The event carries a result: {@link #allow()} forces the spawn through vanilla's whole monster predicate,
 * {@link #deny()} stops it, and neither leaves vanilla's answer. A deny is sticky. Valid only during dispatch.
 */
public interface WildsPlacementCheck extends ModuleEvent<Level> {

    /**
     * The resolver's test, for a check that fires for every natural spawn attempt: a {@code MobCategory.MONSTER} type at
     * a position in the Wilds. The type is tested first, so a firing that is not a monster costs no distance lookup.
     */
    static boolean passes(EntityType<?> type, Level level, BlockPos pos) {
        return type.getCategory() == MobCategory.MONSTER && BorderAPI.frontierDistanceAt(level, pos) > 0;
    }

    /** The level the spawn attempt is happening in. */
    default Level level() {
        return context();
    }

    /** The type being checked; no mob of it exists yet. */
    EntityType<?> entityType();

    /** Why the spawn is being attempted (natural, spawner, event and so on). */
    MobSpawnType spawnType();

    /** The candidate position the placement rules are checked against. */
    BlockPos pos();

    ModuleSignal.Verdict verdict();

    void allow();

    void deny();
}
