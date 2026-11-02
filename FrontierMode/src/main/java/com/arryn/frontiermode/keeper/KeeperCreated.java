package com.arryn.frontiermode.keeper;

import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * A Keeper has been created for a progression Border (wiki/frontiermode/architecture/keeper.md § Keeper events).
 * Posted on Satchel's bus by {@link KeeperAPI#createBoss} once the record exists: Border growth, the first Border and
 * {@code /boss attach}. Not a module event: it has no native Forge event. It runs synchronously on the server
 * thread, in the poster's call, after the state change it reports. The modules downstream of the Keeper hear it on
 * their own ingress; the Tell module creates the Border's Tell curve if absent, and the Sentinel module ensures its
 * two curves.
 *
 * @param level    the level the Keeper was created in
 * @param bossId   the new Keeper's boss id
 * @param borderId the Border it was created for
 */
public record KeeperCreated(Level level, UUID bossId, UUID borderId) {
}
