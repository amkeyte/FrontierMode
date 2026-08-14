package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.newconfig.newnew.*;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class JigInfo implements IJigConfigurable {

    public final JigKey<?> key;
    public final SatchelJig<?> jig;

    private final ScopeCoupler coupler;

    private final Map<SatchelScope, ScopeInfo> scopeInfos =
            new ConcurrentHashMap<>();
    private CompiledJigConfig compiled;

    // =============================================================
    // Scope access
    // =============================================================
    private JigBindingConfig<?, ?> binding;
    private JigExecutionConfig execution;
    private JigPoliciesConfig policies;
    private JigBundlesConfig<?> bundles;

    public JigInfo(
            JigKey<?> key,
            SatchelJig<?> jig,
            ScopeCoupler coupler
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.jig = Objects.requireNonNull(jig, "jig");
        this.coupler = Objects.requireNonNull(coupler, "coupler");

        JigKey.validateTypes(key, jig);
    }

    // =============================================================
    // Type metadata
    // =============================================================

    public Optional<ScopeInfo> scopeInfo(SatchelScope scope) {
        return Optional.ofNullable(scopeInfos.get(scope));
    }

    public Collection<ScopeInfo> scopeInfos() {
        return scopeInfos.values();
    }

    public boolean hasScope(SatchelScope scope) {
        return scopeInfos.containsKey(scope);
    }

    public ScopeInfo addScope(SatchelScope scope, Object source) {
        Objects.requireNonNull(scope, "scope");
        requireConfig();

        validateScopeType(scope);

        ScopeInfo info = new ScopeInfo(scope, source);
        info.installJigConfig(compiledConfig());
        info.installJigInfo(this);

        ScopeInfo existing = scopeInfos.putIfAbsent(scope, info);
        if (existing != null) {
            throw new SatchelException.Generic(
                    "Scope already exists for jig " + key + ": " +
                            scope.debugName()
            );
        }

        return info;
    }

    // =============================================================
    // CONFIG (live reference only)
    // =============================================================

    public void removeScope(SatchelScope scope) {
        ScopeInfo removed = scopeInfos.remove(scope);
        if (removed == null) {
            throw new SatchelException.Generic(
                    "Attempted to remove unknown scope from jig " + key +
                            ": " + scope.debugName()
            );
        }
    }

    public Class<?> scopeType() {
        requireConfig();
        return binding().scopeType();
    }

    private void validateScopeType(SatchelScope scope) {
        Class<?> expected = binding().scopeType();

        if (!expected.isInstance(scope)) {
            throw new IllegalStateException(
                    "Scope type mismatch for jig " + key +
                            ": got=" + scope.getClass().getName() +
                            ", expected=" + expected.getName()
            );
        }
    }

    public ScopeCoupler coupler() {
        return coupler;
    }

    @Override
    public String toString() {
        return "JigInfo[" + key + "]";
    }

    @Override
    public void installJigConfig(CompiledJigConfig config) {
        Objects.requireNonNull(config, "config");

        this.compiled = config;
        this.binding = config.binding();
        this.execution = config.execution();
        this.policies = config.policies();
        this.bundles = config.bundles();
    }

    private void requireConfig() {
        if (compiled == null) {
            throw new IllegalStateException(
                    "JigConfig not installed for jig: " + key
            );
        }
    }

    private CompiledJigConfig compiledConfig() {
        requireConfig();
        return compiled;
    }

    @Override
    public JigBindingConfig<?, ?> binding() {
        return binding;
    }

    @Override
    public JigExecutionConfig execution() {
        return execution;
    }

    @Override
    public JigPoliciesConfig policies() {
        return policies;
    }

    @Override
    public JigBundlesConfig<?> bundles() {
        return bundles;
    }

    public Optional<SatchelScope> resolveScope(Object source) {
        requireConfig();

        if (source == null) {
            return Optional.empty();
        }

        Class<?> expectedSource = binding().sourceType();

        if (!expectedSource.isInstance(source)) {
            return Optional.empty();
        }

        var resolver = binding().scopeResolver();
        if (resolver == null) {
            throw new IllegalStateException("resolver cannot be null");
        }

        SatchelScope scope = resolver.resolve(source);

        if (scope == null) {
            return Optional.empty();
        }

        validateScopeType(scope);

        return Optional.of(scope);
    }
}