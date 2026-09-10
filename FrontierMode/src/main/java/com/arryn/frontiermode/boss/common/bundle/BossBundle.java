package com.arryn.frontiermode.boss.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.world.level.Level;

import java.util.Optional;


/**
 * World-scoped bundle hosting {@link BossFixture}.
 *
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
     * SAT_053: BossFixture is the sole durable record of boss identity and must survive a
     * restart (see BossModule.registerBossJig's capabilities/persistence policy). This bundle
     * declares the matching requirement itself; the engine now gates promotion on this, not on
     * the jig-level policy alone.
     */
    @Override
    public int required() {
        return Capability.PERSISTENCE.bit();
    }

    public Level level() {
        return ((LevelScope) scope).level();
    }


    public Optional<BossFixture> boss() {
        return get(FrontierKeys.BOSS);
    }

    public Optional<BossTellFixture> tell() {
        return get(FrontierKeys.BOSS_TELL);
    }

    public Optional<BossGuardiansFixture> guardians() {
        return get(FrontierKeys.BOSS_GUARDIANS);
    }
}
