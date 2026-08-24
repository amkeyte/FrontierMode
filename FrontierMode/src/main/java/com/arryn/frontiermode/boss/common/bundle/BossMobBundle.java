package com.arryn.frontiermode.boss.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

import java.util.Optional;


/**
 * Mob-scoped bundle hosting {@link BossMobFixture}. Needed even though nothing else is registered
 * on {@code MobScope} yet -- ordinary {@code JigConfigValidator} shape, not a footgun; see
 * wiki/satchel/architecture/new-module-checklist.md item 7 and boss.md's "Data model" section.
 *
 * Intentionally boring, same shape as {@code BossBundle}/{@code BordersBundle}.
 */
public final class BossMobBundle extends SatchelBundle {

    public BossMobBundle(
            SatchelScope scope,
            BundleKey<BossMobBundle> key
    ) {
        super(scope, key);
    }


    public Optional<BossMobFixture> bossMob() {
        return get(FrontierKeys.BOSS_MOB);
    }
}
