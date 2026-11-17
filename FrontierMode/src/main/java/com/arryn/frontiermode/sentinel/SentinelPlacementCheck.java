package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * The placement gate of a natural spawn, before any mob exists: the Sentinel module's event for Forge's
 * {@link MobSpawnEvent.SpawnPlacementCheck} (sentinels.md § The daylight rule). Its context is the level. The ingress's
 * resolver passes only a {@code MobCategory.MONSTER} type, spawning naturally, that vanilla's own check has refused, so
 * every other firing is dropped before an event is built and vanilla's answer stands.
 * {@link SentinelJigHandlers#onSpawnPlacementCheck} is its one consumer.
 *
 * <p>The native event carries a result: {@link #allow()} forces the spawn through vanilla's whole monster predicate,
 * {@link #deny()} stops it, and neither leaves vanilla's answer. A deny is sticky. Valid only during dispatch: after the
 * consumer returns, a read of the native event throws, and the values copied at construction stay readable.
 */
public final class SentinelPlacementCheck extends ModuleSignal<MobSpawnEvent.SpawnPlacementCheck, Level> {

    /** The Forge event this module event reflects, for the ingress's translate. */
    static final Class<MobSpawnEvent.SpawnPlacementCheck> NATIVE = MobSpawnEvent.SpawnPlacementCheck.class;

    private final EntityType<?> entityType;
    private final MobSpawnType spawnType;
    private final BlockPos pos;

    SentinelPlacementCheck(MobSpawnEvent.SpawnPlacementCheck forgeEvent, Level level) {
        super(forgeEvent, level);
        this.entityType = Objects.requireNonNull(forgeEvent.getEntityType(), "entityType");
        this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
        this.pos = Objects.requireNonNull(forgeEvent.getPos(), "pos");
    }

    /**
     * The translate's resolver, the filter for a check that fires for every natural spawn attempt: the level for a
     * {@code MobCategory.MONSTER} type spawning naturally that vanilla's own check has refused, empty for anything
     * else. The lift never applies to spawner blocks, eggs or commands.
     */
    static Optional<Level> resolve(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType().getCategory() != MobCategory.MONSTER
                || event.getSpawnType() != MobSpawnType.NATURAL
                || event.getDefaultResult()) {
            return Optional.empty();
        }
        return Optional.of(event.getLevel().getLevel());
    }

    /** The level the spawn attempt is happening in. */
    public Level level() {
        return context();
    }

    /** The type being checked; no mob of it exists yet. */
    public EntityType<?> entityType() {
        return entityType;
    }

    /** Why the spawn is being attempted. */
    public MobSpawnType spawnType() {
        return spawnType;
    }

    /** The candidate position the placement rules are checked against. */
    public BlockPos pos() {
        return pos;
    }
}
