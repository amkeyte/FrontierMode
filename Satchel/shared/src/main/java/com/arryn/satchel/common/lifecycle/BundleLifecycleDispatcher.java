package com.arryn.satchel.common.lifecycle;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

import java.util.Objects;

/**
 * Emits Satchel bundle lifecycle events.
 *
 * <p>
 * This dispatcher does NOT:
 * <ul>
 *   <li>Create bundles</li>
 *   <li>Hydrate bundles</li>
 *   <li>Decide readiness</li>
 *   <li>Invoke bundle logic</li>
 * </ul>
 *
 * <p>
 * It only:
 * <ul>
 *   <li>Emits semantic lifecycle events for bundles</li>
 * </ul>
 */
public final class BundleLifecycleDispatcher {

    private final LogicalFoundation foundation;

    public BundleLifecycleDispatcher(LogicalFoundation foundation) {
        this.foundation = Objects.requireNonNull(foundation, "foundation");
    }

    /* =============================================================
     * Lifecycle signals (per scope + bundle)
     * ========================================================== */

    /**
     * Signal that a bundle has fully loaded and is ready.
     *
     * <p>
     * Must only be called after hydration and readiness checks
     * have completed.
     */
    public void signalBundleLoaded(ScopeInfo scope, SatchelBundle bundle) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(bundle, "bundle");

        emitLoaded(scope, bundle);
    }

    /**
     * Emit a semantic tick for a loaded bundle.
     *
     * <p>
     * Caller is responsible for ensuring bundle execution
     * has already occurred.
     */
    public void signalBundleTick(ScopeInfo scope, SatchelBundle bundle) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(bundle, "bundle");

        emitTick(scope, bundle);
    }

    /**
     * Signal that a bundle is unloading.
     */
    public void signalBundleUnloaded(ScopeInfo scope, SatchelBundle bundle) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(bundle, "bundle");

        emitUnloaded(scope, bundle);
    }

    /* =============================================================
     * Event emission
     * ========================================================== */

    private void emitLoaded(ScopeInfo scope, SatchelBundle bundle) {
        foundation.eventBus()
                .post(new BundleEvent.Loaded(scope, bundle));
    }

    private void emitTick(ScopeInfo scope, SatchelBundle bundle) {
        foundation.eventBus()
                .post(new BundleEvent.Tick(scope, bundle));
    }

    private void emitUnloaded(ScopeInfo scope, SatchelBundle bundle) {
        foundation.eventBus()
                .post(new BundleEvent.Unloaded(scope, bundle));
    }
}
