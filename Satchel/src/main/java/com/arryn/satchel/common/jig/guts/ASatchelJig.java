package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.newconfig.newnew.*;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.throttle.TickThrottler;

import java.util.Objects;
import java.util.Optional;

public abstract class ASatchelJig<S extends SatchelScope>
        implements SatchelJig<S> {

    private JigKey<? extends SatchelJig<S>> key;

    // ------------------------------------------------------------
    // CONFIG (live category references only)
    // ------------------------------------------------------------

    private JigBindingConfig<?,?> binding;
    private JigExecutionConfig execution;
    private JigPoliciesConfig policies;
    private JigBundlesConfig<?> bundles;

    @Override
    public void installJigConfig(CompiledJigConfig config) {
        Objects.requireNonNull(config, "config");

        binding = config.binding();
        execution = config.execution();
        policies = config.policies();
        bundles = config.bundles();
    }

    protected final void requireConfig() {
        if (binding == null) {
            throw new IllegalStateException(
                    "JigConfig not installed: " + debugName()
            );
        }
    }

    @Override public JigBindingConfig<?, ?> binding() { return binding; }
    @Override public JigExecutionConfig execution() { return execution; }
    @Override public JigPoliciesConfig policies() { return policies; }
    @Override public JigBundlesConfig<?> bundles() { return bundles; }

    /* ---------------------------------------------------------------------
     * Context helpers
     * ------------------------------------------------------------------ */

    public ASatchelJig() {
        OUT.TRACE().registerCaller(
                this,
                new TickThrottler.AutoClock(),
                200L,
                "tryMarkScopeReady",
                "handleScopeTick");
    }

    public abstract Class<S> scopeType();

    private LogicalFoundation foundation() {
        return Satchel.require();
    }

    @Override
    public void installKey(JigKey<?> key) {
        Objects.requireNonNull(key, "key");
        if (this.key != null)
            throw new IllegalStateException("Cannot reinstall key");
        this.key = (JigKey<? extends SatchelJig<S>>) key;
    }

    @Override
    public JigKey<? extends SatchelJig<S>> jigKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "Jig key accessed before installation"
            );
        }
        return key;
    }

    protected ScopeCoupler coupler() {
        return Satchel.require()
                .requireJigInfo(jigKey())
                .coupler();
    }

    /* ---------------------------------------------------------------------
     * Hub → Jig entrypoints
     * ------------------------------------------------------------------ */

    @Override
    public void onLoad(ScopeInfo info) {
        Objects.requireNonNull(info, "info");
        requireConfig();

        coupler().onScopeLoad(info);
        foundation().scopeLifecycle().signalScopeLoaded(info);
    }

    @Override
    public void handleExecutionPulse(ScopeInfo info) {
        Objects.requireNonNull(info, "info");
        requireConfig();

        @SuppressWarnings("unchecked")
        S scope = (S) info.scope();
        if (scope == null) return;

        if (policies().diagnostics().enableTracing()) {
            OUT.TRACE().log(
                    this,
                    ":execPulse" + info.debugName(),
                    "Handling Execution Pulse"
            );
        }

        // ---------------------------------------------------------
        // Phase 1: readiness convergence
        // ---------------------------------------------------------

        if (info.phase() == ScopeInfo.Phase.NEW) {

            if (!tryMarkScopeReady(info)) {
                return;
            }

            onLoad(info);
            return;
        }

        // ---------------------------------------------------------
        // Phase 2: steady-state execution
        // ---------------------------------------------------------

        if (info.phase() != ScopeInfo.Phase.LOADED) {
            return;
        }

        if (!execution().lifecycle().participatesInExecutionPulse()) {
            return;
        }

        coupler().onExecutionPulse(info);
    }

    @Override
    public void onTick(ScopeInfo info) {
        Objects.requireNonNull(info, "info");
        requireConfig();

        if (!execution().lifecycle().participatesInTick()) {
            return;
        }

        if (policies().diagnostics().enableTracing()) {
            OUT.TRACE().log(
                    this,
                    "jigTick" + info.debugName(),
                    "Handling Jig Tick"
            );
        }

        coupler().onJigTick(info);
        foundation().scopeLifecycle().signalScopeTick(info);
    }

    @Override
    public void onUnload(ScopeInfo info) {
        Objects.requireNonNull(info, "info");
        requireConfig();

        foundation().scopeLifecycle().signalScopeUnloaded(info);
        coupler().onScopeUnload(info);
    }

    /* ---------------------------------------------------------------------
     * Bundle access
     * ------------------------------------------------------------------ */

    private ScopeInfo requireInfo(SatchelScope scope) {
        return foundation().requireScopeInfo(jigKey(), scope);
    }

    @Override
    public <B extends SatchelBundle> B get(
            SatchelScope scope,
            BundleKey<B> key
    ) {
        return coupler().get(requireInfo(scope), key);
    }

    @Override
    public <B extends SatchelBundle> Optional<B> ask(
            SatchelScope scope,
            BundleKey<B> key
    ) {
        return coupler().ask(requireInfo(scope), key);
    }

    @Override
    public <B extends SatchelBundle> B getOrCreate(
            SatchelScope scope,
            BundleKey<B> key
    ) {
        return coupler().getOrCreate(requireInfo(scope), key);
    }

    /* ---------------------------------------------------------------------
     * Readiness routing
     * ------------------------------------------------------------------ */

    private boolean tryMarkScopeReady(ScopeInfo info) {

        if (info.isReady()) {
            return true;
        }

        boolean accepted = coupler().tryMarkReady(info);

        if (policies().diagnostics().enableTracing()) {
            if (accepted) {
                OUT.TRACE().log("[SatchelJig] Scope accepted readiness: " + info.debugName());
            } else {
                OUT.TRACE().log(
                        this,
                        "tryMarkScopeReady",
                        "Scope not ready yet (policy refused): "
                                + info.debugName()
                );
            }
        }

        return accepted;
    }

    /* ---------------------------------------------------------------------
     * Debug
     * ------------------------------------------------------------------ */

    public String debugName() {
        return getClass().getSimpleName()
                + "@" + System.identityHashCode(this);
    }
}