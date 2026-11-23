package com.arryn.frontiermode.core;


/**
 * Entry point for {@code core} (wiki/frontiermode/architecture/core.md): where code lives that two or more modules use.
 * It has no jig, bundle, fixture or Trinket, and its ingress is in each line folder. The module is a root with no
 * registration, called first, before any module that registers a {@link ScaleSource}.
 */
public final class CoreModule {

    private CoreModule() {
    }

    public static void init() {
    }
}
