package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.CompiledJigConfig;
import com.arryn.satchel.common.jig.config.JigPolicies;

import java.util.Optional;

/**
 * Per-scope facade a jig's consumers actually call through -- bundle access/creation,
 * lifecycle forwarding from the engine, and scope-readiness. One instance exists per jig
 * kind ({@code LevelScopeCoupler}, {@code MobScopeCoupler}, {@code PlayerScopeCoupler}),
 * not per scope instance.
 *
 * <p>
 * This is the "Coupler" touch point named in SAT_053's ownership split: it owns
 * scope-readiness, a different axis on a different clock than a bundle's own lifecycle
 * state machine -- a scope can be gate-able independently of whether any one of its
 * bundles has loaded (a future BlockJig's chunk actually loaded vs. ticket-requested; a
 * future {@code BossMobScopeCoupler} answering "is this mob actually materialized," not
 * just "does the entity resolve"). {@link AScopeCoupler} supplies a working, fully
 * permissive default implementation of every hook below; as of SAT_053's audit
 * (2026-09-10), every real coupler in either repo rides that default untouched -- this is
 * a real, available extension point with zero current overrides, not a proven pattern to
 * copy from an existing one.
 */
public interface ScopeCoupler extends IJigConfigurable {

    /**
     * Get a bundle that must already exist, running {@link #beforeAccess}/{@link
     * #afterAccess} around the engine lookup. Throws {@code SatchelException
     * .BundleNotFound} if the bundle hasn't been created yet for this scope -- see
     * {@link #getOrCreate} for the create-on-demand variant.
     */
    <B extends SatchelBundle> B get(
            ScopeInfo info,
            BundleKey<B> key
    );


    /**
     * Non-throwing variant of {@link #get} -- an empty {@code Optional} instead of a
     * thrown exception, both when the bundle doesn't exist yet and when a hook denies
     * access.
     */
    <B extends SatchelBundle> Optional<B> ask(
            ScopeInfo info,
            BundleKey<B> key
    );

    /**
     * Get a bundle, creating it via {@link #beforeCreate}/{@link #afterCreate} if it
     * doesn't exist yet for this scope. The common entry point for "first access to this
     * scope's bundle, whichever comes first."
     */
    <B extends SatchelBundle> B getOrCreate(
            ScopeInfo info,
            BundleKey<B> key
    );


    /**
     * Coupler-level readiness check/latch -- meant to inspect external state (a player's
     * network sync state, a chunk's load state, a boss mob's materialization) and flip
     * this scope ready if that state now allows it. Distinct from {@link
     * JigPolicies.Readiness#mode()}, which only selects IMMEDIATE vs. DEFERRED; this is
     * the hook a DEFERRED coupler would actually implement. {@link AScopeCoupler} has no
     * default body for this one (every concrete coupler must supply it), but as of
     * SAT_053's audit (2026-09-10) every real coupler runs IMMEDIATE mode, so none of them
     * currently give it real logic.
     */
    boolean markReady(ScopeInfo info);

    /**
     * Attempt to mark this scope ready if it isn't already.
     * @return true if the scope is ready -- either it already was, or {@link #markReady}
     *     just succeeded. False otherwise.
     */
    boolean tryMarkReady(ScopeInfo info);

    /**
     * Whether this scope is currently ready, per {@link JigPolicies.Readiness#mode()}:
     * IMMEDIATE always returns true, DEFERRED delegates to {@link ScopeInfo#isReady()}.
     */
    boolean isReady(ScopeInfo info);


    void requireReady(ScopeInfo info, BundleKey<?> key);


    /**
     * Verdict returned by the before/after access and create hooks below. {@link #DENY}
     * turns the corresponding operation into a thrown {@code SatchelException
     * .AccessDenied} (or, for {@link #ask}, an empty {@code Optional}).
     */
    enum AccessDecision {
        ALLOW,
        DENY
    }
    void onScopeLoad(ScopeInfo info);

    void onExecutionPulse(ScopeInfo info);

    void onJigTick(ScopeInfo info);

    void onScopeUnload(ScopeInfo info);

    /**
     * Called immediately before the engine's per-jig-tick pass for this scope, only when
     * {@link JigPolicies.Lifecycle#participatesInTick()} is set. {@link AScopeCoupler}'s
     * default is a no-op; unoverridden anywhere in either repo as of SAT_053's audit
     * (2026-09-10).
     */
    void beforeTick(ScopeInfo info);

    /**
     * Called immediately after the engine's per-jig-tick pass for this scope. Same
     * no-op default and unoverridden-everywhere status as {@link #beforeTick}.
     */
    void afterTick(ScopeInfo info);

    /**
     * Called immediately before the engine unloads this scope's bundles. Same no-op
     * default and unoverridden-everywhere status as {@link #beforeTick}.
     */
    void beforeUnload(ScopeInfo info);

    /**
     * Called immediately after the engine unloads this scope's bundles. Same no-op
     * default and unoverridden-everywhere status as {@link #beforeTick}.
     */
    void afterUnload(ScopeInfo info);

    /**
     * Called before a bundle read via {@link #get}/{@link #ask}. Returning {@link
     * AccessDecision#DENY} refuses the read. {@link AScopeCoupler}'s default always
     * returns {@link AccessDecision#ALLOW}; unoverridden anywhere in either repo as of
     * SAT_053's audit (2026-09-10). A real candidate: a scope-level access gate
     * independent of any one bundle's own data (refusing all access while a boss mob's
     * scope is mid-materialization, say).
     */
    ScopeCoupler.AccessDecision beforeAccess(ScopeInfo info, BundleKey<?> key);

    /**
     * Called after a bundle read succeeds, with the resolved bundle in hand. Returning
     * {@link AccessDecision#DENY} invalidates the read after the fact. Same permissive
     * default and unoverridden-everywhere status as {@link #beforeAccess}.
     */
    ScopeCoupler.AccessDecision  afterAccess(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle);

    /**
     * Called before a bundle is created via {@link #getOrCreate}. Same shape, same
     * permissive default, same unoverridden-everywhere status as {@link #beforeAccess}.
     */
    ScopeCoupler.AccessDecision beforeCreate(ScopeInfo info, BundleKey<?> key);

    /**
     * Called after a bundle is created, with the new bundle in hand, before {@link
     * #afterAccess} runs on that same bundle. Same shape, same permissive default, same
     * unoverridden-everywhere status as {@link #beforeAccess}.
     */
    ScopeCoupler.AccessDecision  afterCreate(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle);

    void installEngine(ScopeEngine engine);
}
