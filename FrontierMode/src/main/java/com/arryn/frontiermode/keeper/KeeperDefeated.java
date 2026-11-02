package com.arryn.frontiermode.keeper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * A Keeper has been defeated (wiki/frontiermode/architecture/keeper.md § Keeper events). Posted on Satchel's bus by
 * the Keeper's {@code onDefeat}, first, after the base has marked the record defeated. Not a module event: it has
 * no native Forge event. It runs synchronously on the server thread, in the poster's call. The Sentinel module
 * queues its kill.
 *
 * @param level    the level the Keeper was in
 * @param bossId   the Keeper's boss id
 * @param credited the credited player; empty for a defeat with no killer
 */
public record KeeperDefeated(Level level, UUID bossId, Optional<ServerPlayer> credited) {
}
