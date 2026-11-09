package com.arryn.frontiermode.boss.common;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.Boss;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

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
     * Whether a body of {@code type} can belong to this kind. The base asks every kind, through
     * {@link BossKinds#anyBody}, as its cheap gate before any roster lookup: the render and size reads run for every
     * entity, so a type no kind can have bounces first. Must be cheap and must not touch the level.
     */
    boolean canBeBody(EntityType<?> type);

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
     * What a defeat does, for both defeat paths: a death in combat, and a forced one ({@code /boss defeat}). Called
     * after the base has marked the record defeated and scheduled the flush. {@code where} is where the defeat
     * happened: a death location for a combat defeat, the record's own position for a forced one. {@code body} is the
     * boss's body, present for a combat defeat and empty for a forced one (the base has already dismissed it).
     * {@code credited} is empty for a defeat with no killer. The outcome says whether the defeat went through and what
     * to tell the command that forced it; the combat path ignores it.
     */
    DefeatOutcome onDefeat(Level level, UUID bossId, BlockPos where, Optional<Mob> body,
                           Optional<Player> credited);

    /**
     * Subscribes the kind's mob-side abilities, with their settings, to {@code host}, the dynamic host of the boss's
     * mob scope, on the side this runs on (boss.md § Abilities, § Sides). Called as the scope attaches, after the base
     * has subscribed its own, for a boss whose record is alive. A subscriber lives with the scope, so nothing here
     * is saved.
     */
    void subscribeAbilities(Level level, Boss boss, DynamicTrinket host);

    /**
     * Subscribes the kind's record-side abilities, with their settings, to {@code host}, the dynamic host of the
     * level scope, on the side this runs on (boss.md § Abilities, § Sides). Called by the roster the moment it
     * builds the {@code Boss}, before any lifecycle step, for every {@code Boss} it builds, alive or not: a record's
     * {@code alive} changes while its {@code Boss} exists, and an ability decides for itself what a defeated record
     * means. A subscriber lives with the scope, so nothing here is saved; the roster takes the subscribers back out
     * when it drops the {@code Boss}.
     */
    void subscribeRecordAbilities(Level level, Boss boss, DynamicTrinket host);

    /**
     * Whether this kind offers {@code /boss add}, hand placement. A command asks before it calls {@link #handPlace},
     * and answers that the verb does not apply to a kind that says no. The default is a kind that does not offer it.
     */
    default boolean offersHandPlace() {
        return false;
    }

    /**
     * Whether this kind offers {@code /boss attach}, pairing onto an existing Border. A command asks before it calls
     * {@link #attachToBorder}, and answers that the verb does not apply to a kind that says no. The default is a kind
     * that does not offer it.
     */
    default boolean offersAttach() {
        return false;
    }

    /**
     * {@code /boss add}: creates a record of this kind at a position an admin chose, with no Border, and commits the
     * position at once. Only called for a kind that {@link #offersHandPlace() offers it}; calling it on one that does
     * not is a programming error. Empty means the kind rejected the arguments (logged).
     */
    default Optional<BossRecord> handPlace(Level level, BlockPos position, int layer) {
        throw new UnsupportedOperationException("[Boss] handPlace(): kind \"" + key() + "\" does not offer the verb.");
    }

    /**
     * {@code /boss attach}: creates a record of this kind for an existing Border and clears the Border's pending
     * attach. Only called for a kind that {@link #offersAttach() offers it}; calling it on one that does not is a
     * programming error. Empty means the kind rejected the Border (logged).
     */
    default Optional<BossRecord> attachToBorder(Level level, Border border) {
        throw new UnsupportedOperationException("[Boss] attachToBorder(): kind \"" + key() + "\" does not offer the verb.");
    }
}
