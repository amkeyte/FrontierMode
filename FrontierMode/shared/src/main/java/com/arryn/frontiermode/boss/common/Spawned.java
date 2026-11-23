package com.arryn.frontiermode.boss.common;

import net.minecraft.world.entity.Mob;

/**
 * What a kind's {@link BossKind#spawn} returns (boss.md § Spawn): the mob it added to the level, and the body
 * size multiplier the base stores in the record's {@code scale} when it materializes.
 */
public record Spawned(Mob mob, float scale) {
}
