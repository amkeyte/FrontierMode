package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.jig.config.*;

public interface IJigConfigurable {
    void installJigConfig(CompiledJigConfig config);

    JigBindingConfig<?, ?> binding();

    JigExecutionConfig execution();

    JigPoliciesConfig policies();

    JigBundlesConfig<?> bundles();
}
