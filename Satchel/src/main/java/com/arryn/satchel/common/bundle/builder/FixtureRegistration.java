package com.arryn.satchel.common.bundle.builder;

import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.FixtureKey;

import java.util.function.Supplier;

public record FixtureRegistration<T extends SatchelFixture>(
        FixtureKey<T> key,
        Supplier<? extends T> factory
) {}
