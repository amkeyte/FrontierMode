package com.arryn.frontiermode.border.common.player;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

import java.util.Optional;

/**
 * Player-scoped bundle hosting {@link BorderPlayerStatusFixture}.
 *
 * FRO_026 rewrite: package paths (predecessor {@code satchel.identity}/{@code satchel.bundle}/
 * {@code satchel.jig.guts}) predate the current {@code satchel.common.*} structure; the dead
 * {@code getOrCreateFacet(Class)} stub is replaced with the modern {@code get(FixtureKey)}
 * pattern {@link com.arryn.frontiermode.border.common.bundle.BordersBundle#borders()} already
 * uses.
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
