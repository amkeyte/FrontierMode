package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

/**
 * The position gate of a natural spawn, after the mob exists and has been placed: the Sentinel module's event for the
 * loader's position-check event, on both lines (sentinels.md § The daylight rule). Its context is the mob. Each line's
 * resolver passes only a natural spawn ({@link #passes}), so a spawner's firing is dropped before an event is built.
 * {@link SentinelJigHandlers#onSpawnPositionCheck} is its one consumer.
 *
 * <p>The event carries a result: {@link #allow()} forces the position through, bypassing both the light test and the
 * obstruction test, {@link #deny()} stops it, and neither leaves vanilla's answer. A deny is sticky. Valid only during
 * dispatch.
 */
public interface SentinelPositionCheck extends ModuleEvent<Mob> {

    /** The resolver's test: only a natural spawn. */
    static boolean passes(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL;
    }

    /** The mob whose position is being checked; it already exists and has been placed. */
    default Mob mob() {
        return context();
    }

    /** The level the spawn is happening in. */
    default Level level() {
        return mob().level();
    }

    ModuleSignal.Verdict verdict();

    void allow();

    void deny();
}
