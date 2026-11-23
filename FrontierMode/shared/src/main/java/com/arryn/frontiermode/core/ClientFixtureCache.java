package com.arryn.frontiermode.core;

import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Function;

/**
 * Client-side cache for a fixture reached through Satchel scope lookup, which is too much to do for
 * every entity on every frame. Caches per level and, while unresolved (the normal state for the
 * first moments after joining, before the first parcel lands), retries at most once a second. Used by
 * Boss's {@code BossMarkers} and the Sentinel module's {@code SentinelMarkers}; not for the server side, which
 * asks rarely and isn't cached. A holder evicts it on {@code ScopeEvent.Unloaded} through {@link #evict}
 * (new-module-checklist.md item 9), so the cache never keeps the last world's level and fixture alive. No client
 * classes here, so it loads on a dedicated server.
 */
public final class ClientFixtureCache<T> {

    private static final long RETRY_TICKS = 20;

    private final Function<Level, Optional<T>> resolver;
    private Level cachedLevel;
    private T cached;
    private long nextResolveTick;

    public ClientFixtureCache(Function<Level, Optional<T>> resolver) {
        this.resolver = resolver;
    }

    public Optional<T> get(Level level) {
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

    /**
     * Drops the cache if it holds {@code level}: called when that level's scope unloads. Any other level is a no-op,
     * so the caller needs no side or jig check.
     */
    public void evict(Level level) {
        if (level == cachedLevel) {
            cachedLevel = null;
            cached = null;
            nextResolveTick = 0;
        }
    }
}
