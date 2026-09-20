package com.arryn.frontiermode.bosstell.common;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

import java.util.Optional;

/**
 * Player-scoped bundle hosting {@link CompassPlayerFixture} -- its own bundle, not
 * {@code BorderPlayerBundle}, per special-compass.md's "PlayerScope fixture" section. Mirrors
 * {@code BorderPlayerBundle}'s shape exactly.
 */
public final class CompassPlayerBundle extends SatchelBundle {

    public CompassPlayerBundle(
            SatchelScope owner,
            BundleKey<CompassPlayerBundle> key
    ) {
        super(owner, key);
    }

    public Optional<CompassPlayerFixture> compass() {
        return get(FrontierKeys.COMPASS_PLAYER);
    }
}
