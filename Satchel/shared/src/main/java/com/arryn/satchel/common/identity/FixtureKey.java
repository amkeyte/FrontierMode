package com.arryn.satchel.common.identity;


import com.arryn.satchel.common.fixture.SatchelFixture;

import java.util.UUID;

public final class FixtureKey<T extends SatchelFixture>
        extends SatchelKey<T> {

    public FixtureKey(String name, Class<T> type) {
        super(name, type);
    }

    public FixtureKey(UUID id, String name, Class<T> type) {
        super(id, name, type);
    }
}
