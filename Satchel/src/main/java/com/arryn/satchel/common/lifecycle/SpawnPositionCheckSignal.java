package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a freshly created mob's chosen position is checked --
 * wraps {@link MobSpawnEvent.PositionCheck}, the second, separate gate in the spawn flow. It fires
 * after the mob exists and after {@link SpawnPlacementCheckSignal}'s check has already passed.
 *
 * <p>
 * <b>Not cancelable -- use {@link #allow()}/{@link #deny()}.</b> The event is {@code @HasResult}.
 * Left unset, Forge takes {@code mob.checkSpawnRules(level, spawnType) &&
 * mob.checkSpawnObstruction(level)}. <b>{@link #allow()} bypasses both of those checks, not just
 * the rules one.</b> A subscriber that allows must re-run {@code mob().checkSpawnObstruction(...)}
 * itself if it still wants a mob kept out of solid blocks or fluid; that method is public on {@link
 * Mob}.
 *
 * <p>
 * Fires both for natural spawning and for spawner blocks. {@link #spawnType()} says which
 * ({@link MobSpawnType#SPAWNER} for a spawner), and {@link #spawner()} is the spawner when Forge
 * has one -- it is {@code null} for natural spawns, and may still be {@code null} for a spawner.
 *
 * <p>
 * Posted once, unscoped, to the whole bus -- every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerSpawnIngress.onSpawnPositionCheck} (SAT_086). Server-only by
 * construction -- {@link MobSpawnEvent.PositionCheck}'s own doc: "only on the logical server."
 */
public final class SpawnPositionCheckSignal extends SatchelSignal<MobSpawnEvent.PositionCheck> {

    private final Mob mob;
    private final Level level;
    private final MobSpawnType spawnType;
    @Nullable
    private final BaseSpawner spawner;

    public SpawnPositionCheckSignal(MobSpawnEvent.PositionCheck forgeEvent) {
        super(forgeEvent);
        this.mob = Objects.requireNonNull(forgeEvent.getEntity(), "mob");
        this.level = Objects.requireNonNull(mob.level(), "level");
        this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
        this.spawner = forgeEvent.getSpawner();
    }

    /** The mob whose position is being checked; it already exists and has been placed. */
    public Mob mob() {
        return mob;
    }

    /** The level the spawn is happening in. */
    public Level level() {
        return level;
    }

    /** Why this spawn is happening -- {@link MobSpawnType#SPAWNER} for a spawner block. */
    public MobSpawnType spawnType() {
        return spawnType;
    }

    /** The spawner responsible, or {@code null} for a natural spawn (and sometimes a spawner one). */
    @Nullable
    public BaseSpawner spawner() {
        return spawner;
    }
}
