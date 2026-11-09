package com.arryn.frontiermode.sentinel.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.sentinel.common.fixture.SentinelFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.Optional;

/**
 * Level-scoped bundle hosting {@link SentinelFixture} on {@code SENTINEL_JIG} -- its own bundle, so sentinel
 * churn never resends the boss records. See sentinels.md's "SentinelFixture: the synced record".
 *
 * <p>Declares NETWORKING only: the fixture holds sentinels currently present, which rebuild from
 * presence after a restart, so nothing is persisted.
 */
public final class SentinelBundle extends SatchelBundle {

    public SentinelBundle(
            LevelScope scope,
            BundleKey<SentinelBundle> key
    ) {
        super(scope, key);
    }

    @Override
    public int required() {
        return Capability.NETWORKING.bit();
    }

    public Optional<SentinelFixture> markers() {
        return get(FrontierKeys.SENTINEL);
    }
}
