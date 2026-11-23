package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;

/**
 * The position gate of a spawn, after the mob exists and has been placed: the module's event for Forge's
 * {@link MobSpawnEvent.PositionCheck} (feral.md § How Feral hooks the game). Its context is the mob. The
 * ingress's resolver passes only a {@code MobCategory.MONSTER} mob standing in the Wilds, so every other
 * firing is dropped before an event is built. {@link FeralSpawnGateHandlers#onPositionCheck} is its one
 * consumer.
 *
 * <p>The native event carries a result: {@link #allow()} forces the position through, bypassing both the
 * light test and the obstruction test, {@link #deny()} stops it, and neither leaves vanilla's answer. A
 * deny is sticky. Valid only during dispatch: after the consumer returns, a read of the native event
 * throws, and the level copied at construction stays readable.
 */
public final class WildsPositionCheck extends ModuleSignal<MobSpawnEvent.PositionCheck, Mob> {

    private final Level level;

    WildsPositionCheck(MobSpawnEvent.PositionCheck forgeEvent, Mob mob) {
        super(forgeEvent, mob);
        //noinspection resource
        this.level = Objects.requireNonNull(mob.level(), "level");
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
