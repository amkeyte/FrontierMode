package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.CompiledJigConfig;
import com.arryn.satchel.common.newconfig.newnew.IJigConfigurable;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;

import java.util.Optional;

public interface ScopeCoupler extends IJigConfigurable {

    <B extends SatchelBundle> B get(
            ScopeInfo info,
            BundleKey<B> key
    );


    <B extends SatchelBundle> Optional<B> ask(
            ScopeInfo info,
            BundleKey<B> key
    );
    <B extends SatchelBundle> B getOrCreate(
            ScopeInfo info,
            BundleKey<B> key
    );


    /**
    coupler policy validation (inspect external state)
     */
    boolean markReady(ScopeInfo info);

    /**
     * Attempt to mark the requireJig system as ready.
     * @return true if the requireJig system is ready, false if not. default true.
     */
    boolean tryMarkReady(ScopeInfo info);

    /**
     * Checks if the requireJig system is ready
     * @return true/false
     */
    boolean isReady(ScopeInfo info);


    void requireReady(ScopeInfo info, BundleKey<?> key);


    enum AccessDecision {
        ALLOW,
        DENY
    }
    void onScopeLoad(ScopeInfo info);

    void onExecutionPulse(ScopeInfo info);

    void onJigTick(ScopeInfo info);

    void onScopeUnload(ScopeInfo info);

    void beforeTick(ScopeInfo info);

    void afterTick(ScopeInfo info);

    void beforeUnload(ScopeInfo info);

    void afterUnload(ScopeInfo info);

    ScopeCoupler.AccessDecision beforeAccess(ScopeInfo info, BundleKey<?> key);

    ScopeCoupler.AccessDecision  afterAccess(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle);

    ScopeCoupler.AccessDecision beforeCreate(ScopeInfo info, BundleKey<?> key);

    ScopeCoupler.AccessDecision  afterCreate(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle);

    void installEngine(ScopeEngine engine);
}
