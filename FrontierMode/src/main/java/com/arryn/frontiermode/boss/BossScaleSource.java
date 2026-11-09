package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.core.ScaleSource;
import net.minecraft.world.entity.Entity;

/**
 * The Boss module's {@link ScaleSource} for {@code core} (wiki/frontiermode/architecture/core.md § Scale sources): a
 * boss's size comes from its record's {@code scale} through {@link BossMarkers}. The Sentinel module registers its own
 * source, so a mob is a boss or a Sentinel and the sources are exclusive. Stateless and side-agnostic.
 */
final class BossScaleSource implements ScaleSource {

    /** The cheap gate: a boss-eligible type. */
    @Override
    public boolean mayScale(Entity entity) {
        return BossMarkers.isEligible(entity);
    }

    @Override
    public float scaleOf(Entity entity) {
        return BossMarkers.scaleOf(entity);
    }
}
