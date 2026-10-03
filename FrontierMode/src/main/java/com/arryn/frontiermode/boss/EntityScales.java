package com.arryn.frontiermode.boss;

import net.minecraft.world.entity.Entity;

/**
 * The one size multiplier the hitbox and render handlers apply to any entity: the boss's scale
 * for a boss, otherwise the sentinel's for a sentinel, otherwise {@code 1.0}. Both handlers
 * ({@link BossScaleEvents}, {@code BossScaleClientEvents}) ask this rather than either lookup, so
 * there is one place that states the precedence and no second subscriber per scale source.
 */
public final class EntityScales {

    private EntityScales() {
    }

    public static float scaleOf(Entity entity) {
        float boss = BossMarkers.scaleOf(entity);
        return boss != 1.0F ? boss : SentinelMarkers.scaleOf(entity);
    }

    /** True if {@code entity} could carry a scale: boss-eligible, or a sentinel candidate. */
    public static boolean canBeScaled(Entity entity) {
        return BossMarkers.isEligible(entity) || SentinelMarkers.isCandidate(entity);
    }
}
