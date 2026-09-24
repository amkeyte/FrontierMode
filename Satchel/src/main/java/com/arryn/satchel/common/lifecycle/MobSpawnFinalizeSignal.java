package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} just before {@code Mob#finalizeSpawn} runs, wrapping {@link
 * MobSpawnEvent.FinalizeSpawn}.
 *
 * <p>
 * <b>{@link #cancel()} does NOT prevent the mob from spawning.</b> This is a real, sharp Forge
 * gotcha, not an oversight here — per {@link MobSpawnEvent.FinalizeSpawn}'s own doc, canceling the
 * event only skips {@code Mob#finalizeSpawn}'s own initialization; the entity still spawns
 * regardless. To actually prevent the spawn, call {@link #denySpawn()} instead, which wraps the
 * event's own separate {@code setSpawnCancelled(boolean)} — a flag stored on the entity itself,
 * not the event, and the only mechanism Forge actually enforces the spawn against (see {@code
 * ForgeInternalHandler#builtinMobSpawnBlocker}). Both mechanisms are independent; a subscriber
 * wanting to block a spawn outright should call {@link #denySpawn()}, not {@link #cancel()}.
 *
 * <p>
 * Posted once, unscoped, to the whole bus — every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerSpawnIngress.onFinalizeSpawn} (SAT_076). Server-only by construction —
 * {@link MobSpawnEvent.FinalizeSpawn}'s own doc: "only fired on the logical server."
 */
public final class MobSpawnFinalizeSignal extends SatchelSignal<MobSpawnEvent.FinalizeSpawn> {

    private final Mob mob;
    private final Level level;
    private final MobSpawnType spawnType;

    public MobSpawnFinalizeSignal(MobSpawnEvent.FinalizeSpawn forgeEvent) {
        super(forgeEvent);
        this.mob = Objects.requireNonNull(forgeEvent.getEntity(), "mob");
        this.level = Objects.requireNonNull(mob.level(), "level");
        this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
    }

    /** The mob about to be spawned. */
    public Mob mob() {
        return mob;
    }

    /** The level the mob is spawning into. */
    public Level level() {
        return level;
    }

    /** Why this spawn is happening (natural, spawner, event, etc.). */
    public MobSpawnType spawnType() {
        return spawnType;
    }

    /**
     * Actually prevents this mob from spawning — see this class's own doc for why {@link
     * #cancel()} alone doesn't. Idempotent-safe to call more than once.
     */
    public void denySpawn() {
        forgeEvent().setSpawnCancelled(true);
    }
}
