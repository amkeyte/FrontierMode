package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.identity.JigKey;

import java.util.Objects;

public final class CompiledJigConfig {

    private final JigKey<?> jigKey;

    private final JigBindingConfig<?,?> binding;
    private final JigExecutionConfig execution;
    private final JigPoliciesConfig policies;
    private final JigBundlesConfig<?> bundles;

    public CompiledJigConfig(
            JigKey<?> jigKey,
            JigBindingConfig<?,?> binding,
            JigExecutionConfig execution,
            JigPoliciesConfig policies,
            JigBundlesConfig<?> bundles
    ) {
        this.jigKey = Objects.requireNonNull(jigKey);
        this.binding = Objects.requireNonNull(binding);
        this.execution = Objects.requireNonNull(execution);
        this.policies = Objects.requireNonNull(policies);
        this.bundles = Objects.requireNonNull(bundles);
    }

    public JigKey<?> jigKey() { return jigKey; }
    public JigBindingConfig<?,?> binding() { return binding; }
    public JigExecutionConfig execution() { return execution; }
    public JigPoliciesConfig policies() { return policies; }
    public JigBundlesConfig<?> bundles() { return bundles; }
}