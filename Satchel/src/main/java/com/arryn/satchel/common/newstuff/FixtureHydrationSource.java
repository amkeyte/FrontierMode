package com.arryn.satchel.common.newstuff;

import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.FixtureKey;

import java.util.function.Supplier;

public interface FixtureHydrationSource {
    boolean hydrate(SatchelFixture facet);

    <T extends SatchelFixture> T hydrate(
            FixtureKey<T> key,
            Supplier<? extends T> factory
    );
}
