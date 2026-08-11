package com.arryn.satchel.common.bundle.builder;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.identity.BundleKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class BundleFactoryEntry<T extends SatchelBundle> {

    public final BundleKey<T> key;
    public final Function<SatchelScope, T> bundleFactory;
    public final List<FixtureRegistration<?>> fixtureRegistrations =
            new ArrayList<>();

    public BundleFactoryEntry(
            BundleKey<T> key,
            Function<SatchelScope, T> bundleFactory
    ) {
        this.key = key;
        this.bundleFactory = bundleFactory;
    }
}
