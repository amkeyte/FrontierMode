package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.config.CompiledJigConfig;
import com.arryn.satchel.common.lifecycle.ScopeEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * The interface a jig kind implements -- {@code LevelJig}, {@code PlayerJig}, and {@code
 * MobJig} are the three real implementors today, each extending {@link ASatchelJig}, the
 * concrete base that supplies this interface's actual dispatch logic. Owns lifecycle
 * dispatch (load/execution-pulse/tick/unload, see {@code ASatchelJig.handleExecutionPulse}/
 * {@code onTick} for the phase-branching that drives {@link ScopeEvent} onto {@code
 * SatchelEventBus}) and delegates bundle access ({@link #get}/{@link #ask}/{@link
 * #getOrCreate}) to this jig's own {@link ScopeCoupler}.
 */
public interface SatchelJig<S extends SatchelScope> extends IJigConfigurable {
    //possible make public if a need arises to get a key without a context instance.
    JigKey<?> jigKey();

    /**
     * Called once a {@code NEW} scope's readiness converges. Forwards to this jig's {@link
     * ScopeCoupler#onScopeLoad} then posts {@link ScopeEvent}{@code .Loaded} onto {@code
     * SatchelEventBus}.
     */
    void onLoad(ScopeInfo info);

    /**
     * Called once per foundation pulse for a scope in the {@code NEW} phase (readiness not
     * yet converged) or {@code LOADED} phase (steady state) -- see {@code
     * ASatchelJig.handleExecutionPulse} for the actual phase branching. Persistence flush and
     * client sync both ride this pulse, not {@link #onTick}, so a jig that needs either must
     * declare {@code participatesInExecutionPulse(true)} independently of tick participation.
     *
     * @param info the scope this pulse is for.
     */
    void handleExecutionPulse(ScopeInfo info);

    /**
     * Called once per jig tick for a {@code LOADED} scope, gated on {@code
     * participatesInTick()}. This is what posts {@link ScopeEvent}{@code .Tick} onto the bus
     * -- a jig kind's own tick-driven domain logic (Border's growth, a coupler's {@code
     * onJigTick}) runs from here, not from {@link #handleExecutionPulse}.
     */
    void onTick(ScopeInfo info);

    /**
     * Called once this scope is torn down (real teardown, driven by {@code
     * LevelEvent.Unload}/{@code PlayerLoggedOutEvent}, or, for {@code MobJig}, an
     * unresolvable UUID at reconcile time). Posts {@link ScopeEvent}{@code .Unloaded} onto
     * {@code SatchelEventBus} before forwarding to this jig's {@link
     * ScopeCoupler#onScopeUnload}.
     */
    void onUnload(ScopeInfo info);

    /**
     * Poll-driven scope reconciliation, called once per {@link JigInfo} every foundation pulse,
     * before the per-{@link ScopeInfo} handleExecutionPulse/onTick walk (see
     * FoundationLifecycleDispatcher.pulse()). Default no-op -- only a jig kind whose sources
     * aren't discovered via a Forge join/load event (i.e. MobJig, RM_SAT_021) overrides this;
     * LevelJig and PlayerJig inherit the no-op unchanged.
     *
     * @param info the JigInfo this jig kind is compiled into for this side -- gives a
     *             reconciling jig its own registered scopes ({@code info.scopeInfos()}) and its
     *             own {@code JigKey} ({@code info.key}) without needing a separate parameter.
     */
    default void reconcile(JigInfo info) {
        // no-op
    }

    /**
     * Get a bundle that must already exist for {@code scope} under this jig. Throws {@code
     * SatchelException.BundleNotFound} if it hasn't been created yet -- see {@link
     * #getOrCreate} for the create-on-demand variant. Delegates to this jig's own {@link
     * ScopeCoupler#get}.
     */
    <B extends SatchelBundle> B get(
            SatchelScope scope,
            BundleKey<B> key
    );

    /** Non-throwing variant of {@link #get} -- an empty {@code Optional} instead of a thrown
     * exception. Delegates to {@link ScopeCoupler#ask}. */
    <B extends SatchelBundle> Optional<B> ask(
            SatchelScope scope,
            BundleKey<B> key
    );

    /** Get a bundle for {@code scope} under this jig, creating it if it doesn't exist yet.
     * Delegates to {@link ScopeCoupler#getOrCreate}. */
    <B extends SatchelBundle> B getOrCreate(
            SatchelScope scope,
            BundleKey<B> key
    );

    /** Derives this jig kind's scope-identity UUID from a raw source object (a {@code Level},
     * a {@code ServerPlayer}, a {@code Mob}) -- see each jig kind's own resolver/scope class
     * for the exact derivation. */
    UUID determineUUID(Object source);

    /** Resolves a raw source object into this jig kind's own {@link SatchelScope} subtype,
     * or {@code null} if this jig doesn't recognize the source. */
    S resolveScope(Object source);

    /**
     * Unused -- no call site anywhere in either repo. Coupler instantiation is owned by
     * {@code LogicalFoundation}/{@code JigConfigCompiler}, driven by the compiled config's
     * own {@code couplerType}, not by asking the jig instance for its coupler class.
     */
    @Deprecated
    Class<? extends ScopeCoupler> couplerClass();

    void installKey(JigKey<?> key);
    Class<S> scopeType();

}
