package com.arryn.frontiermode.core;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;

/**
 * Entry point for {@code core} (wiki/frontiermode/architecture/core.md): where code lives that two or more modules use.
 * It has no jig, bundle, fixture or Trinket. The module is a root: one registration line, called first, before any
 * module that registers a {@link ScaleSource}.
 */
public final class CoreModule {

    private CoreModule() {
    }

    public static void init() {
        Satchel.registerIngress(JigPolicies.SideApplicability.BOTH, () -> CoreIngress.build());
    }
}
