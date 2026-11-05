package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.core.ScaleSource;
import net.minecraft.world.entity.Entity;

/**
 * The Sentinel module's {@link ScaleSource} for {@code core} (wiki/frontiermode/architecture/core.md § Scale sources):
 * a Sentinel's size comes from the Sentinel lookup, {@link SentinelMarkers}, which reads the record on the mob on the
 * server and the synced fixture on the client. Stateless and side-agnostic; registered from
 * {@code SentinelModule.init()}.
 */
final class SentinelScaleSource implements ScaleSource {

    /** The cheap gate: a Sentinel candidate. */
    @Override
    public boolean mayScale(Entity entity) {
        return SentinelMarkers.isCandidate(entity);
    }

    @Override
    public float scaleOf(Entity entity) {
        return SentinelMarkers.scaleOf(entity);
    }
}
