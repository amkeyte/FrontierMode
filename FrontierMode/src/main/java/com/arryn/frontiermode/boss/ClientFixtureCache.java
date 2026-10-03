package com.arryn.frontiermode.boss;

import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Function;

/**
 * Client-side cache for a fixture reached through Satchel scope lookup, which is too much to do for
 * every entity on every frame. Caches per level and, while unresolved (the normal state for the
 * first moments after joining, before the first parcel lands), retries at most once a second. Used by
 * {@link BossMarkers} and {@link SentinelMarkers}; not for the server side, which asks rarely and
 * isn't cached. No client classes here, so it loads on a dedicated server.
 */
final class ClientFixtureCache<T> {

    private static final long RETRY_TICKS = 20;

    private final Function<Level, Optional<T>> resolver;
    private Level cachedLevel;
    private T cached;
    private long nextResolveTick;

    ClientFixtureCache(Function<Level, Optional<T>> resolver) {
        this.resolver = resolver;
    }

    Optional<T> get(Level level) {
        if (level != cachedLevel) {
            cachedLevel = level;
            cached = null;
            nextResolveTick = 0;
        }
        if (cached == null) {
            long now = level.getGameTime();
            if (now < nextResolveTick) {
                return Optional.empty();
            }
            cached = resolver.apply(level).orElse(null);
            if (cached == null) {
                nextResolveTick = now + RETRY_TICKS;
                return Optional.empty();
            }
        }
        return Optional.of(cached);
    }
}
