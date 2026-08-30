package com.arryn.frontiermode.border.common.bundle;

import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

/**
 * World-scoped bundle hosting {@link BordersFixture}.
 *
 * This bundle is intentionally boring: no logic, no state beyond fixtures.
 */
public final class BordersBundle extends SatchelBundle {

    public BordersBundle(
            SatchelScope scope,
            BundleKey<BordersBundle> key
    ) {
        super(scope, key);
    }
}
