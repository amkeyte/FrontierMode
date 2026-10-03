package com.arryn.frontiermode.boss.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.SentinelPublicFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.Optional;

/**
 * Level-scoped bundle hosting {@link SentinelPublicFixture} -- its own bundle, not folded into
 * {@link BossPublicBundle}, so sentinel churn never resends the boss records. See
 * sentinels.md's "SentinelPublicFixture".
 *
 * <p>Declares NETWORKING only: the fixture holds sentinels currently present, which rebuild from
 * presence after a restart, so nothing is persisted.
 */
public final class SentinelPublicBundle extends SatchelBundle {

    public SentinelPublicBundle(
            LevelScope scope,
            BundleKey<SentinelPublicBundle> key
    ) {
        super(scope, key);
    }

    @Override
    public int required() {
        return Capability.NETWORKING.bit();
    }

    public Optional<SentinelPublicFixture> markers() {
        return get(FrontierKeys.SENTINEL_PUBLIC);
    }
}
