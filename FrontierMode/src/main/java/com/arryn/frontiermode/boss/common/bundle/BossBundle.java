package com.arryn.frontiermode.boss.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
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

    public Level level() {
        return ((LevelScope) scope).level();
    }


    public Optional<BossFixture> boss() {
        return get(FrontierKeys.BOSS);
    }

    public Optional<BossTellFixture> tell() {
        return get(FrontierKeys.BOSS_TELL);
    }
}
