package com.arryn.frontiermode.keeper;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * A player has newly hit a Keeper (wiki/frontiermode/architecture/keeper.md § Keeper events). Posted on Satchel's
 * bus by the {@code Defense} ability, once per new credited-player hit. Not a module event: it has no native Forge
 * event. It runs synchronously on the server thread, in the poster's call, after the hit it reports. The Sentinel
 * module raises that Keeper's Sentinels near the fight.
 *
 * @param level  the level the Keeper is in
 * @param bossId the Keeper's boss id
 * @param mob    the Keeper's body
 * @param player the player holding the kill credit: their hit, projectile, or tamed pet
 */
public record KeeperHit(Level level, UUID bossId, Mob mob, Player player) {
}
