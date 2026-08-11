package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.newconfig.newnew.CompiledJigConfig;
import com.arryn.satchel.common.newconfig.newnew.IJigConfigurable;

import java.util.Optional;
import java.util.UUID;

public interface SatchelJig<S extends SatchelScope> extends IJigConfigurable {
    //possible make public if a need arises to get a key without a context instance.
    JigKey<?> jigKey();

    void onLoad(ScopeInfo info);

    /**
     * persistence and hydration are dependent on the world onExecutionPulse, regardless of
     * requireJig assigned events.
     * also used by the coupler for readiness check
     *
     * @param info the execution onExecutionPulse event.
     */
    void handleExecutionPulse(ScopeInfo info);

    /**
     * Jig assigned onExecutionPulse for use by coupler, bundle, or facet objects downstream
     */
    void onTick(ScopeInfo info);

    void onUnload(ScopeInfo info);

    <B extends SatchelBundle> B get(
            SatchelScope scope,
            BundleKey<B> key
    );

    <B extends SatchelBundle> Optional<B> ask(
            SatchelScope scope,
            BundleKey<B> key
    );

    <B extends SatchelBundle> B getOrCreate(
            SatchelScope scope,
            BundleKey<B> key
    );



    UUID determineUUID(Object source);

    S resolveScope(Object source);

    /**
     * Declares the coupler scopeType for this requireJig.
     * Instantiation is owned by Foundation.
     */
    @Deprecated
    Class<? extends ScopeCoupler> couplerClass();

    void installKey(JigKey<?> key);
    Class<S> scopeType();

}
