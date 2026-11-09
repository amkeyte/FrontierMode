package com.arryn.frontiermode.siting;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;

/**
 * Finding a spot that's good for a thing (siting.md): the shared terrain-suitability check, the
 * candidate generators, and the new-world starting-site search. Depends on Border; Boss, structure and
 * nothing else depend on it. The module keeps no jig: the one game event it hears, the new world's spawn
 * choice, reaches it through {@link SitingIngress}.
 */
public final class SitingModule {

    private SitingModule() {
    }

    public static void init() {
        OUT.info("[Siting] init(): Module initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> SitingIngress.build());
    }
}
