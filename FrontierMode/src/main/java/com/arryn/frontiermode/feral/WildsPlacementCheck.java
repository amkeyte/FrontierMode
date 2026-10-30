package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;

/**
 * The placement gate of a natural spawn, before any mob exists: the module's event for Forge's
 * {@link MobSpawnEvent.SpawnPlacementCheck} (feral.md § How Feral hooks the game). Its context is the
 * level. The ingress's resolver passes only a {@code MobCategory.MONSTER} type at a position in the
 * Wilds, so every other firing is dropped before an event is built and vanilla's own checks are
 * unchanged. {@link FeralSpawnGateHandlers#onPlacementCheck} is its one consumer.
 *
 * <p>The native event carries a result: {@link #allow()} forces the spawn through vanilla's whole
 * monster predicate, {@link #deny()} stops it, and neither leaves vanilla's answer. A deny is sticky.
 * Valid only during dispatch: after the consumer returns, a read of the native event throws, and the
 * values copied at construction stay readable.
 */
public final class WildsPlacementCheck extends ModuleSignal<MobSpawnEvent.SpawnPlacementCheck, ServerLevel> {

    private final EntityType<?> entityType;
    private final MobSpawnType spawnType;
    private final BlockPos pos;

    WildsPlacementCheck(MobSpawnEvent.SpawnPlacementCheck forgeEvent, ServerLevel level) {
        super(forgeEvent, level);
        this.entityType = Objects.requireNonNull(forgeEvent.getEntityType(), "entityType");
        this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
        this.pos = Objects.requireNonNull(forgeEvent.getPos(), "pos");
    }

    /** The level the spawn attempt is happening in. */
    public ServerLevel level() {
        return context();
    }

    /** The type being checked; no mob of it exists yet. */
    public EntityType<?> entityType() {
        return entityType;
    }

    /** Why the spawn is being attempted (natural, spawner, event and so on). */
    public MobSpawnType spawnType() {
        return spawnType;
    }

    /** The candidate position the placement rules are checked against. */
    public BlockPos pos() {
        return pos;
    }
}
