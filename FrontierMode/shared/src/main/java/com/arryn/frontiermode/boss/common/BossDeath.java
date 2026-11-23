package com.arryn.frontiermode.boss.common;

import net.minecraft.core.BlockPos;

/**
 * What the base hands a kind to judge a death (boss.md § What counts as a defeat): whether a player is
 * credited (vanilla's kill credit, read by the base), the health players' own damage has removed from the
 * boss so far (the tally on {@code Boss}), the boss's maximum health, and where it died.
 */
public record BossDeath(boolean playerCredited, double playerDamage, double maxHealth, BlockPos position) {
}
