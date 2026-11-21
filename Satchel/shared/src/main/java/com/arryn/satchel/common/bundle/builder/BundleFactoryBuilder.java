package com.arryn.satchel.common.bundle.builder;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.FixtureKey;

import java.util.Objects;
import java.util.function.Supplier;

public final class BundleFactoryBuilder<T extends SatchelBundle> {

    private final BundleFactoryEntry<T> entry;

    public BundleFactoryBuilder(BundleFactoryEntry<T> entry) {
        this.entry = entry;
    }

    public <F extends SatchelFixture>
    BundleFactoryBuilder<T> registerFixture(
            FixtureKey<F> key,
            Supplier<? extends F> factory
    ) {
        Objects.requireNonNull(key, "facet key");
        Objects.requireNonNull(factory, "facet factory");

        entry.fixtureRegistrations.add(
                new FixtureRegistration<>(key, factory)
        );

        return this;
    }
}
