package com.arryn.satchel.common.bundle;

/**
 * SAT_081: engine-installed callback behind {@link SatchelBundle#forceSync()} -- a
 * caller-directed immediate network push, distinct from the fixed-interval timer {@link
 * BundleSyncDelegate} rides. Zero-arg by design, same as {@link BundleFlushDelegate}: the engine
 * binds {@code ScopeInfo}/{@code BundleKey} in at attach time, so the bundle-facing call needs
 * nothing from the caller.
 */
@FunctionalInterface
public interface BundleForceSyncDelegate {
    void push();
}
