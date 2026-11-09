package com.arryn.frontiermode.boss.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.world.level.Level;

import java.util.Optional;


/**
 * World-scoped bundle hosting {@link BossFixture}.
 * <p>
 * This bundle is intentionally boring: no logic, no state beyond fixtures -- same shape as
 * {@code BordersBundle}.
 */
public final class BossBundle extends SatchelBundle {

    public BossBundle(
            LevelScope scope,
            BundleKey<BossBundle> key
    ) {
        super(scope, key);
    }

    /**
     * BossFixture is the sole durable record of boss identity and must survive a
     * restart (see BossModule.registerBossJig's capabilities/persistence policy). This bundle
     * declares the matching requirement itself; the engine gates promotion on this, not on
     * the jig-level policy alone. It is networked too: {@link BossFixture} pushes its changes with {@code forceSync()},
     * which the engine refuses on a bundle that does not declare NETWORKING, and a client bundle that does not declare
     * it never receives a parcel, as with {@code BordersBundle}.
     */
    @Override
    public int required() {
        return Capability.PERSISTENCE.bit() | Capability.NETWORKING.bit();
    }

    public Level level() {
        return ((LevelScope) scope).level();
    }


    public Optional<BossFixture> boss() {
        return get(FrontierKeys.BOSS);
    }
}
