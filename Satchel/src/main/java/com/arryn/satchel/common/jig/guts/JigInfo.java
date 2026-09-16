package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.config.*;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime record for one installed jig -- pairs a {@link JigKey}, a {@link SatchelJig}
 * instance, and a {@link ScopeCoupler}, and owns the {@code Map<SatchelScope, ScopeInfo>}
 * of every scope this jig currently tracks. One {@code JigInfo} exists per compiled {@link
 * CompiledJigConfig}, created by {@code LogicalFoundation.installConfigs} during foundation
 * boot -- a {@code sideApplicability = BOTH} config produces two independent {@code
 * JigInfo}s, one per side's own foundation boot, not one shared instance.
 *
 * <p>
 * A {@code JigInfo} can own many {@link ScopeInfo}s (one {@code LevelJig} tracks one per
 * loaded dimension); a {@link ScopeInfo} belongs to exactly one {@code JigInfo}, installed
 * as its back-reference by {@link #addScope} right after construction -- the only place a
 * {@link ScopeInfo} is ever created.
 */
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

    /**
     * Constructs and registers a new {@link ScopeInfo} for {@code scope} under this jig,
     * installing both this jig's compiled config and the {@link ScopeInfo}'s back-reference
     * to this {@code JigInfo}. Throws {@code SatchelException.Generic} if a {@link ScopeInfo}
     * already exists for this scope under this jig -- callers that only want to introduce a
     * scope once should check {@link #hasScope} first, since this method itself is not
     * idempotent.
     */
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

    /**
     * Evicts the {@link ScopeInfo} for {@code scope} from this jig's tracking map. Throws
     * {@code SatchelException.Generic} if no {@link ScopeInfo} was registered for this scope
     * under this jig.
     */
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

    /**
     * Resolves {@code source} into a {@link SatchelScope} via this jig's own {@code
     * scopeResolver}, returning {@code Optional.empty()} -- not a throw -- for every "not
     * applicable" case: a null source, a source whose type doesn't match this jig's declared
     * {@code sourceType}, or a resolver that itself returns null for this source. A resolved
     * scope's type is validated against this jig's {@code scopeType} before returning, the
     * same check {@link #addScope} applies.
     */
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