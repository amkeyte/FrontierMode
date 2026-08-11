package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.CompiledJigConfig;
import com.arryn.satchel.common.newconfig.newnew.IJigConfigurable;

import java.util.Optional;

/**
 * Satchel-internal execution engine for bundle backends.
 *
 * This interface defines *how* backend operations are executed,
 * not *when* or *why* they occur.
 *
 * Implementations are side-specific but this interface is not.
 */
public interface ScopeEngine extends IJigConfigurable {

    void registerBundleSchema(JigBundles.Schema<?> schema);

    void freezeBundleSchema();

    <B extends SatchelBundle> Optional<B> ask(
            ScopeInfo info,
            BundleKey<B> key
    );

    <T extends SatchelBundle> T get(
            ScopeInfo info,
            BundleKey<T> key
    );

    /**
     * Resolve or create a bundle instance for the given scopeInfo + key.
     *
     * Server implementations perform:
     *  - persistence hydration
     *  - scopeLifecycle sequencing
     *
     * Client implementations perform:
     *  - bundle construction only
     */

    <B extends SatchelBundle> B create(
            ScopeInfo info,
            BundleKey<B> key
    );
    /**
     * Called once per onJigTick (or equivalent observation point).
     *
     * Server implementations flush dirty state.
     * Client implementations are expected to no-op.
     */
    void onExecutionPulse(ScopeInfo info);

    void onJigTick(ScopeInfo info);

    /**
     * Scope is being unloaded.
     *
     * Server implementations flush + destroy bundles.
     * Client implementations clear storage.
     */
    void unload(ScopeInfo info);
}
