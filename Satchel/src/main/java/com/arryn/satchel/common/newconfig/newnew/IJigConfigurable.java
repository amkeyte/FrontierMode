package com.arryn.satchel.common.newconfig.newnew;

public interface IJigConfigurable {
    void installJigConfig(CompiledJigConfig config);

    JigBindingConfig<?, ?> binding();

    JigExecutionConfig execution();

    JigPoliciesConfig policies();

    JigBundlesConfig<?> bundles();
}
