package com.arryn.frontiermode.border.common.player;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

import java.util.Optional;

/**
 * Player-scoped bundle hosting {@link BorderPlayerStatusFixture}.
 * <p>
 * Reaches its fixture through the {@code get(FixtureKey)} pattern
 * {@link com.arryn.frontiermode.boss.common.bundle.BossBundle#boss()} also uses; Border reaches
 * its fixtures through {@code BorderAPI}'s facet resolvers exclusively.
 */
public final class BorderPlayerBundle extends SatchelBundle {

    public BorderPlayerBundle(
            SatchelScope owner,
            BundleKey<BorderPlayerBundle> key
    ) {
        super(owner, key);
    }

    public Optional<BorderPlayerStatusFixture> status() {
        return get(FrontierKeys.BORDER_PLAYER_STATUS);
    }
}
