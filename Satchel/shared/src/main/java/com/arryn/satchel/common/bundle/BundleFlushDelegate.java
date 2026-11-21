package com.arryn.satchel.common.bundle;

/**
 * SAT_068: engine-installed callback behind {@link SatchelBundle#forceWriteToDisk()} -- a
 * caller-directed immediate disk write, distinct from the automatic dirty-driven cadence
 * {@code FlushPolicy} controls. Zero-arg by design: unlike {@link BundleSyncDelegate}, the
 * engine binds {@code ScopeInfo}/{@code BundleKey} into the delegate at attach time (they don't
 * change for a bundle's lifetime), so the bundle-facing call needs nothing from the caller.
 */
@FunctionalInterface
public interface BundleFlushDelegate {
    void flush();
}
