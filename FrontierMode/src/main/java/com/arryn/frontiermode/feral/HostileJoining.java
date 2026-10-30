package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.Objects;

/**
 * A hostile mob is joining a level, fresh or loaded from disk: the module's event for Forge's
 * {@link EntityJoinLevelEvent} (feral.md § How Feral hooks the game). Its context is the mob. The
 * ingress's resolver passes only a {@code MobCategory.MONSTER} mob on the server, so a client join, a
 * non-mob entity and a passive mob are dropped before an event is built.
 * {@link FeralTagging#onHostileJoining} is its one consumer.
 *
 * <p>It offers no veto. Valid only during dispatch: after the consumer returns, a read of the native
 * event throws, and the level and the load flag copied at construction stay readable.
 */
public final class HostileJoining extends ModuleSignal<EntityJoinLevelEvent, Mob> {

    private final Level level;
    private final boolean loadedFromDisk;

    HostileJoining(EntityJoinLevelEvent forgeEvent, Mob mob) {
        super(forgeEvent, mob);
        this.level = Objects.requireNonNull(forgeEvent.getLevel(), "level");
        this.loadedFromDisk = forgeEvent.loadedFromDisk();
    }

    /** The hostile that is joining the level. */
    public Mob mob() {
        return context();
    }

    /** The level it is joining. */
    public Level level() {
        return level;
    }

    /** True for a mob rehydrated with its chunk, false for one that has just come into being. */
    public boolean loadedFromDisk() {
        return loadedFromDisk;
    }
}
