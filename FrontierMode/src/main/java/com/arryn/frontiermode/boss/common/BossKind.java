package com.arryn.frontiermode.boss.common;

import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The strategy a boss's kind supplies (boss.md § BossKind): what differs between kinds, while the base
 * record supplies what does not. The hooks take the {@code Level} so a kind reaches its own fixture and the
 * other modules' APIs; a kind returns plain values.
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
}
