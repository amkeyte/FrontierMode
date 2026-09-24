package com.arryn.frontiermode.boss.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.BossPublicFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.Optional;

/**
 * FRO_145: level-scoped bundle hosting {@link BossPublicFixture} -- its own bundle, not folded
 * into {@code BossBundle}, so the client-syncable "is this entity a boss" signal never shares a
 * sync surface with {@code BossFixture}'s server-only boss position. See boss.md's "Client-side
 * visual marker".
 *
 * <p>Intentionally boring, same shape as {@code BossBundle}/{@code BordersBundle}. Declares
 * NETWORKING (without it the client-side bundle never receives a parcel and stays stuck CREATED --
 * the FRO_115 lesson on {@code BordersBundle}) and PERSISTENCE (the level jig's config allows it,
 * and every existing synced LevelJig in this codebase pairs the two).
 */
public final class BossPublicBundle extends SatchelBundle {

    public BossPublicBundle(
            LevelScope scope,
            BundleKey<BossPublicBundle> key
    ) {
        super(scope, key);
    }

    @Override
    public int required() {
        return Capability.PERSISTENCE.bit() | Capability.NETWORKING.bit();
    }

    public Optional<BossPublicFixture> markers() {
        return get(FrontierKeys.BOSS_PUBLIC);
    }
}
