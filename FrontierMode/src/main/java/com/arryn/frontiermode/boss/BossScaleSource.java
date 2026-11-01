package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.core.ScaleSource;
import net.minecraft.world.entity.Entity;

/**
 * The Boss module's {@link ScaleSource} for {@code core} (wiki/frontiermode/architecture/core.md § Scale sources): a
 * boss's size comes from its record's {@code scale} through {@link BossMarkers}. Until the Sentinel module registers
 * its own source it also answers for Sentinels, asking {@link SentinelMarkers}. Stateless and side-agnostic.
 */
final class BossScaleSource implements ScaleSource {

    /** The cheap gate: a boss-eligible type, or a Sentinel candidate. */
    @Override
    public boolean mayScale(Entity entity) {
        return BossMarkers.isEligible(entity) || SentinelMarkers.isCandidate(entity);
    }

    @Override
    public float scaleOf(Entity entity) {
        float boss = BossMarkers.scaleOf(entity);
        return boss != 1.0F ? boss : SentinelMarkers.scaleOf(entity);
    }
}
