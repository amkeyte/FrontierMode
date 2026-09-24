package com.arryn.satchel.common.lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a mob's spawn-placement rules are being checked, before
 * the mob object itself even exists — wraps {@link MobSpawnEvent.SpawnPlacementCheck}.
 *
 * <p>
 * <b>Not {@code @Cancelable} — use {@link #allow()}/{@link #deny()}, not {@link #cancel()}.</b>
 * {@link MobSpawnEvent.SpawnPlacementCheck} is {@code @HasResult}: {@link #cancel()} would throw
 * {@code UnsupportedOperationException} on this signal. {@link #allow()} forces the spawn attempt
 * to proceed regardless of vanilla's own placement rules; {@link #deny()} aborts it; leaving both
 * unset defaults to vanilla's own result ({@link #defaultResult()}).
 *
 * <p>
 * Posted once, unscoped, to the whole bus — every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerSpawnIngress.onSpawnPlacementCheck} (SAT_076). Server-only by
 * construction — {@link MobSpawnEvent.SpawnPlacementCheck}'s own doc: "only fired on the logical
 * server."
 */
public final class SpawnPlacementCheckSignal extends SatchelSignal<MobSpawnEvent.SpawnPlacementCheck> {

    private final EntityType<?> entityType;
    private final Level level;
    private final MobSpawnType spawnType;
    private final BlockPos pos;
    private final boolean defaultResult;

    public SpawnPlacementCheckSignal(MobSpawnEvent.SpawnPlacementCheck forgeEvent) {
        super(forgeEvent);
        this.entityType = Objects.requireNonNull(forgeEvent.getEntityType(), "entityType");
        this.level = Objects.requireNonNull(forgeEvent.getLevel().getLevel(), "level");
        this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
        this.pos = Objects.requireNonNull(forgeEvent.getPos(), "pos");
        this.defaultResult = forgeEvent.getDefaultResult();
    }

    /** The type of entity being checked -- no real {@code Mob} instance exists yet at this point. */
    public EntityType<?> entityType() {
        return entityType;
    }

    /** The level the spawn attempt is happening in. */
    public Level level() {
        return level;
    }

    /** Why this spawn is being attempted (natural, spawner, event, etc.). */
    public MobSpawnType spawnType() {
        return spawnType;
    }

    /** The candidate position placement rules are being checked against. */
    public BlockPos pos() {
        return pos;
    }

    /** What vanilla's own placement check would decide, if nothing here overrides it. */
    public boolean defaultResult() {
        return defaultResult;
    }
}
