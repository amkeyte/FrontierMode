package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.newconfig.newnew.*;
import net.minecraft.world.level.Level;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative runtime record for a single scope instance.
 *
 * Lifecycle truth only. No policy enforcement.
 */
public final class ScopeInfo implements IJigConfigurable {

    private final SatchelScope scope;
    private final Object source;

    private Phase phase = Phase.NEW;

    private final Instant createdAt = Instant.now();
    private Instant loadedAt;
    private Instant unloadedAt;

    private long lastExecutionPulse = -1;
    private long lastJigTick = -1;

    public ScopeInfo(
            SatchelScope scope,
            Object source
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.source = source;

        // Was OUT.debug("[ScopeInfo] CREATED ...") -- a ScopeInfo is constructed for every scope
        // of every entity in the world (a bat, a chicken, anything MobJig ever sees), so this
        // fired once per mob spawn forever, confirmed routine and not just a boss-path signal by
        // the time it was actually watched in a real playtest log. Same "expected, confirmed
        // behavior, remove rather than downgrade" call as AScopeCoupler's BundleNotFound line.
    }

    // ---------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------

    public SatchelScope scope() {
        return scope;
    }

    public Object source() {
        return source;
    }

    public UUID scopeId() {
        return scope.uuid();
    }

    public String debugName() {
        return scope.debugName();
    }

    @SuppressWarnings("unchecked")
    public <S extends SatchelScope> S scopeAs() {
        return (S) scope;
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    public Phase phase() {
        return phase;
    }

    public void setPhase(Phase next) {
        Objects.requireNonNull(next, "phase");
        requireConfig();

        if (this.phase == next) {
            if (next == Phase.LOADED) {
                throw new IllegalStateException("Cannot load a scope more than once");
            }
            return;
        }

//        // Optional lifecycle validation hook via config
//        if (!execution().lifecycle()..allowsTransition(this.phase, next)) {
//            throw new IllegalStateException(
//                    "Lifecycle policy forbids transition: "
//                            + this.phase + " -> " + next
//            );
//        }

        // Was OUT.debug("[ScopeInfo] PHASE transition ...") -- fires on every phase change for
        // every scope of every entity (NEW -> LOADED on every mob spawn, alongside CREATED
        // above), same routine-and-confirmed noise, removed for the same reason.

        this.phase = next;

        if (next == Phase.LOADED) {
            loadedAt = Instant.now();
        }
        if (next == Phase.UNLOADED) {
            unloadedAt = Instant.now();
        }
    }

    public boolean isReady() {
        requireConfig();

        switch (policies().readiness().mode()) {
            case IMMEDIATE:
                return true;
            case DEFERRED:
                return phase == Phase.LOADED;
            default:
                return phase == Phase.LOADED;
        }
    }

    // ---------------------------------------------------------------------
    // Execution Tracking
    // ---------------------------------------------------------------------

    void markExecutionPulse(long tick) {
        lastExecutionPulse = tick;
    }

    void markJigTick(long tick) {
        lastJigTick = tick;
    }

    public long lastExecutionPulse() {
        return lastExecutionPulse;
    }

    public long lastJigTick() {
        return lastJigTick;
    }

    // ---------------------------------------------------------------------
    // Phase Enum
    // ---------------------------------------------------------------------

    public enum Phase {
        NEW,
        LOADED,
        UNLOADING,
        UNLOADED
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
        Objects.requireNonNull(config, "config");
        binding = config.binding();
        execution = config.execution();
        policies = config.policies();
        bundles = config.bundles();
    }

    private void requireConfig() {
        if (binding == null) {
            throw new IllegalStateException("JigConfig not installed for scope: " + debugName());
        }
    }

    @Override public JigBindingConfig<?, ?> binding() { return binding; }
    @Override public JigExecutionConfig execution() { return execution; }
    @Override public JigPoliciesConfig policies() { return policies; }
    @Override public JigBundlesConfig<?> bundles() { return bundles; }

    @Override
    public String toString() {
        return "ScopeInfo[" +
                scope.debugName() +
                ", phase=" + phase +
                "]";
    }
    public Level referenceLevel() {
        requireConfig();

        if (binding().referenceLevelResolver() == null) {
            return null;
        }

        return binding()
                .referenceLevelResolver()
                .apply(scopeAs());
    }
    // ---------------------------------------------------------------------
    // Owning JigInfo (back-reference)
    // ---------------------------------------------------------------------

    private JigInfo jigInfo;

    /**
     * Installs the back-reference to the {@link JigInfo} that owns this scope. Called once, by
     * {@link JigInfo#addScope}, right after this {@code ScopeInfo} is constructed -- the only
     * place a {@code ScopeInfo} is ever created, and the only place its owning {@code JigInfo} is
     * naturally in scope as {@code this}.
     */
    void installJigInfo(JigInfo jigInfo) {
        if (this.jigInfo != null) {
            throw new IllegalStateException("Cannot reinstall owning JigInfo for scope: " + debugName());
        }
        this.jigInfo = Objects.requireNonNull(jigInfo, "jigInfo");
    }

    public JigInfo jigInfo() {
        return jigInfo;
    }
}