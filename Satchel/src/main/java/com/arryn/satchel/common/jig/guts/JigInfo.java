package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.config.*;
import com.arryn.satchel.common.trinket.TrinketDecl;
import com.arryn.satchel.common.trinket.TrinketTick;
import net.minecraftforge.fml.LogicalSide;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
    private final int registrationIndex;
    private final LogicalFoundation foundation;

    private final Map<SatchelScope, ScopeInfo> scopeInfos =
            new ConcurrentHashMap<>();

    /**
     * The same scopes by id, kept beside the map above in {@link #addScope} and {@link
     * #removeScope}. A lookup by id needs no scope instance, so a resolver on a hot event does not
     * allocate one per firing.
     */
    private final Map<UUID, ScopeInfo> scopeInfosById =
            new ConcurrentHashMap<>();
    private CompiledJigConfig compiled;

    // =============================================================
    // Scope access
    // =============================================================
    private JigBindingConfig<?, ?> binding;
    private JigExecutionConfig execution;
    private JigPoliciesConfig policies;
    private JigBundlesConfig<?> bundles;

    /**
     * @param registrationIndex this jig's position in the order its config was registered, which is
     *                          the order the foundation walks jigs
     * @param foundation        the foundation this jig is installed in: the source of the scope
     *                          creation counter, the global Trinket list, the event bus and the side
     */
    public JigInfo(
            JigKey<?> key,
            SatchelJig<?> jig,
            ScopeCoupler coupler,
            int registrationIndex,
            LogicalFoundation foundation
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.jig = Objects.requireNonNull(jig, "jig");
        this.coupler = Objects.requireNonNull(coupler, "coupler");
        this.registrationIndex = registrationIndex;
        this.foundation = Objects.requireNonNull(foundation, "foundation");

        JigKey.validateTypes(key, jig);
    }

    /**
     * The foundation this jig is installed in.
     */
    public LogicalFoundation foundation() {
        return foundation;
    }

    /**
     * The side of the foundation this jig is installed in. A {@code BOTH} config yields two {@code
     * JigInfo}s, one per side.
     */
    public LogicalSide side() {
        return foundation.side();
    }

    /**
     * The foundation's global Trinket list this jig's scopes belong to.
     */
    public TrinketTick trinketTick() {
        return foundation.trinketTick();
    }

    /**
     * The Trinkets this jig declares, in declaration order.
     */
    public List<TrinketDecl<?>> trinketDecls() {
        requireConfig();
        return compiled.trinkets().declarations();
    }

    /**
     * Where this jig's config sits in registration order -- the order the booter ran the
     * registrations. The foundation walks jigs in this order, and it is the second key of the
     * Trinket sort.
     */
    public int registrationIndex() {
        return registrationIndex;
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
     * This jig's scope with the given id, if it has one, without a scope instance to look it up
     * with. Scope equality is id-based, so this is the same answer as {@link #scopeInfo(SatchelScope)}
     * for a scope with that id.
     */
    public Optional<ScopeInfo> scopeInfo(UUID scopeId) {
        return Optional.ofNullable(scopeInfosById.get(scopeId));
    }

    public boolean hasScope(UUID scopeId) {
        return scopeInfosById.containsKey(scopeId);
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

        ScopeInfo info = new ScopeInfo(scope, source, foundation.nextScopeSequence());
        info.installJigConfig(compiledConfig());
        info.installJigInfo(this);

        ScopeInfo existing = scopeInfos.putIfAbsent(scope, info);
        if (existing != null) {
            throw new SatchelException.Generic(
                    "Scope already exists for jig " + key + ": " +
                            scope.debugName()
            );
        }
        scopeInfosById.put(scope.uuid(), info);

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
        scopeInfosById.remove(scope.uuid(), removed);
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