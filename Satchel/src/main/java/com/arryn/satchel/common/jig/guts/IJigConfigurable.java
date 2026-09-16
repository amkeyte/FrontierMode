package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.jig.config.*;

/**
 * Shared contract for anything that holds a live reference to one jig's four compiled
 * config category lenses -- {@link JigBindingConfig}, {@link JigExecutionConfig}, {@link
 * JigPoliciesConfig}, {@link JigBundlesConfig} -- installed via a single {@link
 * CompiledJigConfig}.
 *
 * <p>
 * {@link JigInfo}, {@link ScopeInfo}, {@link ScopeEngine}, {@link ScopeCoupler}, and {@link
 * SatchelJig} all implement or extend this -- every runtime object in the jig machinery
 * that needs to read a jig's own compiled config reaches it through these same four
 * accessors rather than each type inventing its own.
 *
 * <p>
 * {@code installJigConfig} is not itself guaranteed to be called exactly once. {@link
 * JigInfo}, {@link ScopeInfo}, and {@code ASatchelJig} each receive their own dedicated
 * instance and are installed into once in practice, but nothing here enforces that -- none
 * of the current implementers reject a second call. {@code ScopeEngine_Server}/{@code
 * ScopeEngine_Client} are the sharp exception: one shared per-side engine singleton has
 * {@code installJigConfig} called once per compiled jig config on that side, each call
 * overwriting the engine's own fields rather than accumulating -- so those fields on the
 * engine itself only ever reflect whichever jig installed last (see SAT_023). A caller
 * reading a specific jig's config off a shared implementer like the engine must read it
 * from the {@link ScopeInfo}/{@link JigInfo} passed in, never off {@code this}.
 */
public interface IJigConfigurable {

    /**
     * Installs this compiled jig's config. See class doc for how many times this is
     * actually called, and by what, depending on the implementer.
     */
    void installJigConfig(CompiledJigConfig config);

    /** This jig's identity/binding category. */
    JigBindingConfig<?, ?> binding();

    /** This jig's when-it-participates category. */
    JigExecutionConfig execution();

    /** This jig's declared constraints/guarantees category. */
    JigPoliciesConfig policies();

    /** This jig's bundle schema category. */
    JigBundlesConfig<?> bundles();
}
