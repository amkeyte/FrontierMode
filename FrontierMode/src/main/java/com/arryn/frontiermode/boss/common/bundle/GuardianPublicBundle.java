package com.arryn.frontiermode.boss.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.GuardianPublicFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.Optional;

/**
 * Level-scoped bundle hosting {@link GuardianPublicFixture} -- its own bundle, not folded into
 * {@link BossPublicBundle}, so guardian churn never resends the boss records. See
 * guardian-mobs.md's "GuardianPublicFixture".
 *
 * <p>Declares NETWORKING only: the fixture holds guardians currently present, which rebuild from
 * presence after a restart, so nothing is persisted.
 */
public final class GuardianPublicBundle extends SatchelBundle {

    public GuardianPublicBundle(
            LevelScope scope,
            BundleKey<GuardianPublicBundle> key
    ) {
        super(scope, key);
    }

    @Override
    public int required() {
        return Capability.NETWORKING.bit();
    }

    public Optional<GuardianPublicFixture> markers() {
        return get(FrontierKeys.GUARDIAN_PUBLIC);
    }
}
