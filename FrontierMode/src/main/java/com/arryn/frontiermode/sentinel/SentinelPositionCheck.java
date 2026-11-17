package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * The position gate of a natural spawn, after the mob exists and has been placed: the Sentinel module's event for
 * Forge's {@link MobSpawnEvent.PositionCheck} (sentinels.md § The daylight rule). Its context is the mob. The ingress's
 * resolver passes only a natural spawn, so a spawner's firing is dropped before an event is built.
 * {@link SentinelJigHandlers#onSpawnPositionCheck} is its one consumer.
 *
 * <p>The native event carries a result: {@link #allow()} forces the position through, bypassing both the light test
 * and the obstruction test, {@link #deny()} stops it, and neither leaves vanilla's answer. A deny is sticky. Valid only
 * during dispatch: after the consumer returns, a read of the native event throws, and the level copied at construction
 * stays readable.
 */
public final class SentinelPositionCheck extends ModuleSignal<MobSpawnEvent.PositionCheck, Mob> {

    /** The Forge event this module event reflects, for the ingress's translate. */
    static final Class<MobSpawnEvent.PositionCheck> NATIVE = MobSpawnEvent.PositionCheck.class;

    private final Level level;

    SentinelPositionCheck(MobSpawnEvent.PositionCheck forgeEvent, Mob mob) {
        super(forgeEvent, mob);
        //noinspection resource
        this.level = Objects.requireNonNull(mob.level(), "level");
    }

    /** The translate's resolver: the mob for a natural spawn, empty for any other. */
    static Optional<Mob> resolve(MobSpawnEvent.PositionCheck event) {
        return event.getSpawnType() == MobSpawnType.NATURAL ? Optional.of(event.getEntity()) : Optional.empty();
    }

    /** The mob whose position is being checked; it already exists and has been placed. */
    public Mob mob() {
        return context();
    }

    /** The level the spawn is happening in. */
    public Level level() {
        return level;
    }
}
