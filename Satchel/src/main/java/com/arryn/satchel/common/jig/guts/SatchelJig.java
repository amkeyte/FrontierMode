package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.Satchel;
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
