package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.*;
import com.arryn.satchel.common.util.out.OUT;

import java.util.Objects;
import java.util.Optional;

public abstract class AScopeCoupler implements ScopeCoupler {

    private ScopeEngine engine;

    // ---------------------------------------------------------------------
    // Engine binding
    // ---------------------------------------------------------------------

    @Override
    public final void installEngine(ScopeEngine engine) {
        Objects.requireNonNull(engine, "engine");
        if (this.engine != null) {
            throw new IllegalStateException("Coupler already bound to engine");
        }
        this.engine = engine;
        OUT.debug("Engine installed for " + debugName());
    }

    protected final ScopeEngine engine() {
        if (engine == null) {
            throw new IllegalStateException("Engine not installed: " + debugName());
        }
        return engine;
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    @Override
    public final <B extends SatchelBundle> Optional<B> ask(
            ScopeInfo info,
            BundleKey<B> key
    ) {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(key, "key");
        requireConfig();

        if (!isReady(info)) return Optional.empty();

        if (beforeAccess(info, key) == AccessDecision.DENY) {
            return Optional.empty();
        }

        Optional<B> bundle = engine().ask(info, key);

        if (bundle.isPresent()
                && afterAccess(info, key, bundle.get()) == AccessDecision.DENY) {
            return Optional.empty();
        }

        return bundle;
    }

    @Override
    public final <B extends SatchelBundle> B get(
            ScopeInfo info,
            BundleKey<B> key
    ) {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(key, "key");
        requireConfig();

        requireReady(info, key);

        if (beforeAccess(info, key) == AccessDecision.DENY) {
            throw new SatchelException.AccessDenied("Hook refused access.");
        }

        B bundle = engine().get(info, key);

        if (afterAccess(info, key, bundle) == AccessDecision.DENY) {
            throw new SatchelException.AccessDenied("Hook refused bundle validation.");
        }

        return bundle;
    }

    @Override
    public final <B extends SatchelBundle> B getOrCreate(
            ScopeInfo info,
            BundleKey<B> key
    ) {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(key, "key");
        requireConfig();

        requireReady(info, key);

        // BundleNotFound here is the expected, routine signal for "no bundle yet, fall through
        // and create one" -- true for every scope's first-ever getOrCreate() call (a freshly
        // spawned mob, a freshly loaded level, ...), not an anomaly. Used to log at WARN as dev-
        // tracking while this fallback path was being built; confirmed correct and removed now
        // that it's just noise on every single new scope, forever, for the life of the world.
        try {
            return get(info, key);
        } catch (SatchelException.BundleNotFound ignored) {
            // fall through to create below
        }


        if (beforeCreate(info, key) == AccessDecision.DENY) {
            throw new SatchelException.AccessDenied("Hook refused bundle creation.");
        }

        B bundle = engine().create(info, key);

        if (afterCreate(info, key, bundle) == AccessDecision.DENY) {
            throw new SatchelException.AccessDenied("Hook refused bundle afterCreate.");
        }

        if (afterAccess(info, key, bundle) == AccessDecision.DENY) {
            throw new SatchelException.AccessDenied("Hook refused bundle afterAccess.");
        }

        return bundle;
    }

    // ---------------------------------------------------------------------
    // Lifecycle forwarding
    // ---------------------------------------------------------------------

    @Override
    public void onScopeLoad(ScopeInfo info) {
        requireConfig();
    }

    @Override
    public void onExecutionPulse(ScopeInfo info) {
        requireConfig();
        requireReady(info);

        if (!execution().lifecycle().participatesInExecutionPulse()) return;

        engine().onExecutionPulse(info);
    }

    @Override
    public final void onJigTick(ScopeInfo info) {
        requireConfig();
        requireReady(info);

        if (!execution().lifecycle().participatesInTick()) return;

        beforeTick(info);
        engine().onJigTick(info);
        afterTick(info);
    }

    @Override
    public void onScopeUnload(ScopeInfo info) {
        requireConfig();
        requireReady(info);

        beforeUnload(info);
        engine().unload(info);
        afterUnload(info);
    }

    // ---------------------------------------------------------------------
    // Hooks (default permissive)
    // ---------------------------------------------------------------------

    @Override public void beforeTick(ScopeInfo info) {}
    @Override public void afterTick(ScopeInfo info) {}
    @Override public void beforeUnload(ScopeInfo info) {}
    @Override public void afterUnload(ScopeInfo info) {}

    @Override
    public AccessDecision beforeAccess(ScopeInfo info, BundleKey<?> key) {
        return AccessDecision.ALLOW;
    }

    @Override
    public AccessDecision afterAccess(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle) {
        return AccessDecision.ALLOW;
    }

    @Override
    public AccessDecision beforeCreate(ScopeInfo info, BundleKey<?> key) {
        return AccessDecision.ALLOW;
    }

    @Override
    public AccessDecision afterCreate(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle) {
        return AccessDecision.ALLOW;
    }

// ---------------------------------------------------------------------
// Readiness
// ---------------------------------------------------------------------

    public final boolean isReady(ScopeInfo info) {
        Objects.requireNonNull(info, "info");
        requireConfig();

        switch (policies().readiness().mode()) {

            case IMMEDIATE:
                return true;

            case DEFERRED:
                return info.isReady();

            default:
                throw new IllegalStateException(
                        "Unhandled readiness mode: " +
                                policies().readiness().mode()
                );
        }
    }

    @Override
    public final boolean tryMarkReady(ScopeInfo info) {
        Objects.requireNonNull(info, "info");
        if (isReady(info)) return true;
        return markReady(info);
    }

    public final void requireReady(ScopeInfo info) {
        if (!isReady(info)) {
            throw new SatchelException.ScopeNotReady(
                    "Scope not ready: " + info.debugName()
            );
        }
    }

    @Override
    public final void requireReady(ScopeInfo info, BundleKey<?> key) {
        if (!isReady(info)) {
            throw new SatchelException.AccessFailed(
                    "Coupler accessed before ready: "
                            + debugName()
                            + " scope=" + info.debugName()
                            + " key=" + key.name
            );
        }
    }

    // ---------------------------------------------------------------------
    // Policy helpers (live config reads)
    // ---------------------------------------------------------------------

    public String debugName() {
        return getClass().getSimpleName()
                + "@" + System.identityHashCode(this);
    }

    // ---------------------------------------------------------------------
    // CONFIG (live reference, no flattening)
    // ---------------------------------------------------------------------

    private JigBindingConfig<?,?> binding;
    private JigExecutionConfig execution;
    private JigPoliciesConfig policies;
    private JigBundlesConfig<?> bundles;

    @Override
    public void installJigConfig(CompiledJigConfig config) {
        binding = config.binding();
        execution = config.execution();
        policies = config.policies();
        bundles = config.bundles();
    }

    protected final void requireConfig() {
        if (binding == null) {
            throw new IllegalStateException("JigConfig not installed: " + debugName());
        }
    }

    @Override public JigBindingConfig<?, ?> binding() { return binding; }
    @Override public JigExecutionConfig execution() { return execution; }
    @Override public JigPoliciesConfig policies() { return policies; }
    @Override public JigBundlesConfig<?> bundles() { return bundles; }
}