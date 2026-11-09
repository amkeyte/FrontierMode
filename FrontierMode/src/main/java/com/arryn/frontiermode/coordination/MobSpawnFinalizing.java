package com.arryn.frontiermode.coordination;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;

/**
 * A mob is about to be finalized: the module's event for Forge's {@link MobSpawnEvent.FinalizeSpawn}.
 * Unscoped, because a spawn has no scope to resolve to. {@link SpawnCoordinator} is its one consumer.
 *
 * <p>The constructor runs for every spawn while the event has a subscriber, so it does only three
 * reads. Valid only during dispatch: after the consumer returns, a read of the native event throws,
 * and the three copied facts stay readable.
 */
public final class MobSpawnFinalizing extends ModuleSignal<MobSpawnEvent.FinalizeSpawn, Void> {

    private final Mob mob;
    private final Level level;
    private final MobSpawnType spawnType;

    MobSpawnFinalizing(MobSpawnEvent.FinalizeSpawn forgeEvent) {
        super(forgeEvent, null);
        this.mob = Objects.requireNonNull(forgeEvent.getEntity(), "mob");
        this.level = Objects.requireNonNull(mob.level(), "level");
        this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
    }

    /** The mob about to be finalized. */
    public Mob mob() {
        return mob;
    }

    /** The level it spawns into. */
    public Level level() {
        return level;
    }

    /** Why this spawn is happening (natural, spawner, event, etc.). */
    public MobSpawnType spawnType() {
        return spawnType;
    }

    /**
     * Stops the mob from spawning, by setting the flag Forge enforces against the entity. There is no
     * {@code cancel()} on this event on purpose: cancelling {@code FinalizeSpawn} only skips {@code
     * Mob#finalizeSpawn}'s initialization, and the entity still spawns.
     */
    public void denySpawn() {
        nativeEvent().setSpawnCancelled(true);
    }
}
