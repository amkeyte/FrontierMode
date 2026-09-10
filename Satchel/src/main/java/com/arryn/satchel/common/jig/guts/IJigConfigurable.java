package com.arryn.satchel.common.jig.guts;

public interface IJigConfigurable {
    void installJigConfig(CompiledJigConfig config);

    JigBindingConfig<?, ?> binding();

    JigExecutionConfig execution();

    JigPoliciesConfig policies();

    JigBundlesConfig<?> bundles();
}
