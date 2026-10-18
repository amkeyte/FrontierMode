package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.config.*;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.ContextKeyed;
import com.arryn.satchel.common.lifecycle.SatchelEventBus;
import net.minecraft.world.level.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Authoritative runtime record for a single scope instance.
 *
 * Lifecycle truth only. No policy enforcement.
 */
public final class ScopeInfo implements IJigConfigurable, ContextKeyed {

    private final SatchelScope scope;
    private final Object source;
    private final long creationSequence;

    /** Subscriptions made through {@link #listenFor}, dropped when the scope unloads. */
    private final List<SatchelEventBus.Subscription> subscriptions = new ArrayList<>();

    private Phase phase = Phase.NEW;

    private final Instant createdAt = Instant.now();
    private Instant loadedAt;
    private Instant unloadedAt;

    private long lastExecutionPulse = -1;
    private long lastJigTick = -1;

    public ScopeInfo(
            SatchelScope scope,
            Object source,
            long creationSequence
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.source = source;
        this.creationSequence = creationSequence;

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

    /**
     * Where this scope falls in its foundation's order of creation: a counter drawn once per scope
     * as {@link JigInfo#addScope} builds it. Unique within a foundation and independent of the
     * scope's hash, so it can break a tie between two scopes without depending on map iteration.
     */
    public long creationSequence() {
        return creationSequence;
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

    /**
     * The scope's {@link Level}, for every scope kind and on both sides: a level scope is its level;
     * a player or mob scope is that entity's current level. Unlike {@link #referenceLevel()}, never
     * null for want of a configured resolver.
     *
     * @throws IllegalStateException for a scope kind this method doesn't know
     */
    public Level level() {
        if (scope instanceof LevelScope s) return s.level();
        if (scope instanceof PlayerScope s) return s.player().level();
        if (scope instanceof MobScope s) return s.mob().level();
        throw new IllegalStateException("No level for scope kind " + scope.getClass().getName());
    }

    /**
     * This scope's bundle, through the jig's coupler -- the same readiness gate and hooks as every
     * other bundle read; empty if the scope isn't ready or the bundle doesn't exist yet.
     */
    public <B extends SatchelBundle> Optional<B> ask(BundleKey<B> key) {
        if (jigInfo == null) {
            throw new IllegalStateException("No owning jig for scope: " + debugName());
        }
        return jigInfo.coupler().ask(this, key);
    }

    /**
     * This scope's bundle, created and hydrated if it doesn't exist yet, through the jig's coupler:
     * the same readiness gate and hooks as every other bundle access.
     *
     * <p>
     * For a Trinket that needs persisted state at attach. Trinkets are constructed when the scope
     * loads, before the module's own {@code Loaded} handler creates its bundles, so {@link #ask} is
     * empty in {@code onAttach}; this creates the bundle there instead, and the module's later
     * {@code getOrCreate} finds it. On the server the bundle hydrates from saved data in the call; on
     * the client it is created and waits for its parcel as it always does.
     *
     * @throws SatchelException.AccessFailed if the scope is not ready under its coupler. A jig with
     *         {@code IMMEDIATE} readiness (every jig kind's default) is ready at attach; one with
     *         {@code DEFERRED} readiness is not until its scope has loaded.
     */
    public <B extends SatchelBundle> B getOrCreate(BundleKey<B> key) {
        if (jigInfo == null) {
            throw new IllegalStateException("No owning jig for scope: " + debugName());
        }
        return jigInfo.coupler().getOrCreate(this, key);
    }

    // ---------------------------------------------------------------------
    // Scope-bound event consumers
    // ---------------------------------------------------------------------

    /**
     * The key this scope's subscriptions are filed under on the event bus: the scope's id. A module
     * event whose context is this {@code ScopeInfo} reaches the consumers subscribed here.
     */
    @Override
    public Object contextKey() {
        return scope.uuid();
    }

    /**
     * Hears a module event for this scope only: {@code handler} runs when an event of exactly {@code
     * type} is posted with this scope as its context, at normal priority, and not for a cancelled
     * event.
     *
     * <p>
     * Owned by the scope: Satchel removes the subscription when the scope unloads, even if the
     * subscriber forgets. The handle ends it sooner. Typically called from a Trinket's {@code
     * onAttach}.
     *
     * @return a handle for unsubscribing sooner
     */
    public <E> SatchelEventBus.Subscription listenFor(Class<E> type, Consumer<? super E> handler) {
        return listenFor(type, SatchelEventBus.Priority.NORMAL, false, handler);
    }

    /**
     * {@link #listenFor(Class, Consumer)} with an explicit bus priority and whether a cancelled
     * event is delivered ({@code receiveCancelled}).
     */
    public <E> SatchelEventBus.Subscription listenFor(
            Class<E> type,
            SatchelEventBus.Priority priority,
            boolean receiveCancelled,
            Consumer<? super E> handler
    ) {
        if (jigInfo == null) {
            throw new IllegalStateException("No owning jig for scope: " + debugName());
        }
        if (phase == Phase.UNLOADING || phase == Phase.UNLOADED) {
            throw new IllegalStateException("Cannot listen for " + type.getSimpleName()
                    + " on a scope that is unloading: " + debugName());
        }

        SatchelEventBus.Subscription subscription = jigInfo.foundation().eventBus().subscribe(
                type, contextKey(), priority, receiveCancelled, jigInfo.key.name, handler);
        subscriptions.add(subscription);
        return subscription;
    }

    /**
     * Ends every subscription made through {@link #listenFor}. Called when the scope unloads.
     */
    void dropSubscriptions() {
        for (SatchelEventBus.Subscription s : subscriptions) s.unsubscribe();
        subscriptions.clear();
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