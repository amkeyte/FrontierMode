package com.arryn.frontiermode.keeper.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * World-scoped bundle hosting {@link KeeperFixture}. Intentionally boring: no logic, no state beyond fixtures
 * -- the same shape as {@code BossBundle}.
 */
public final class KeeperBundle extends SatchelBundle {

    public KeeperBundle(
            LevelScope scope,
            BundleKey<KeeperBundle> key
    ) {
        super(scope, key);
    }

    /**
     * {@link KeeperFixture} holds the Keepers' durable records and must survive a restart; the engine gates
     * hydration and flush on the bundle's own declaration as well as the jig's persistence policy.
     */
    @Override
    public int required() {
        return Capability.PERSISTENCE.bit();
    }

    public Level level() {
        return ((LevelScope) scope).level();
    }

    public Optional<KeeperFixture> keepers() {
        return get(FrontierKeys.KEEPER);
    }
}
