package com.arryn.frontiermode.boss.common;

import com.arryn.frontiermode.boss.Boss;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The strategy a boss's kind supplies (boss.md § BossKind): what differs between kinds, while the base
 * record and the {@code Boss} lifecycle supply what does not. A hook takes the {@code Level} so a kind
 * reaches its own fixture and the other modules' APIs, and makes no platform calls itself.
 *
 * <p>A kind that keeps a record of its own writes it in {@link #onCreate} and drops it in
 * {@link #onRemoved}. Registered in {@link BossKinds} by the module that owns the kind.
 */
public interface BossKind {

    /** The key a base record names this kind by, and {@link BossKinds} registers it under. */
    String key();

    /**
     * Writes the kind's own record for {@code bossId} from {@code args}. Returns whether the kind accepted the
     * arguments: {@code false} means it refused, logged why, and wrote nothing. Throws if {@code args} is not
     * the type this kind declares, which is a programming error.
     */
    boolean onCreate(Level level, UUID bossId, BossCreation args);

    /**
     * Whether the boss's entry is whole: the kind's own record exists for {@code bossId}, or the kind keeps
     * none. A reader treats an entry that is not whole as not there yet.
     */
    boolean isComplete(Level level, UUID bossId);

    /** The base record for {@code bossId} has just been removed; the kind drops its own record and does the rest of its removal. */
    void onRemoved(Level level, UUID bossId);

    /**
     * The position to commit for {@code bossId}, once the kind says it is final, or empty to wait. Called each
     * tick while the record has no position.
     */
    Optional<BlockPos> positionReady(Level level, UUID bossId);

    /**
     * Prepares the site at {@code position} before the spawn. Called once the chunk is loaded, each tick the
     * spawn is due, so it must be idempotent.
     */
    SiteOutcome prepareSite(Level level, UUID bossId, BlockPos position);

    /**
     * Spawns the boss's mob at {@code position} and adds it to the level, returning it with the body size the
     * base stores. Empty if the spawn is declined; the base then materializes nothing and asks again next tick.
     */
    Optional<Spawned> spawn(Level level, UUID bossId, BlockPos position);

    /** Whether a death on the combat path is a defeat. The base asks once, before it marks the record defeated. */
    DeathVerdict judgeDeath(Level level, UUID bossId, BossDeath death);

    /**
     * What a defeat does. Called after the base has marked the record defeated and scheduled the flush.
     * {@code credited} is empty for a defeat with no killer.
     */
    void onDefeat(Level level, UUID bossId, Mob mob, Optional<ServerPlayer> credited);

    /**
     * Subscribes the kind's mob-side abilities, with their settings, to {@code host}, the dynamic host of the boss's
     * mob scope, on the side this runs on (boss.md § Abilities, § Sides). Called as the scope attaches, after the base
     * has subscribed its own, for a boss whose record is alive. A subscriber lives with the scope, so nothing here
     * is saved.
     */
    void subscribeAbilities(Level level, Boss boss, DynamicTrinket host);
}
