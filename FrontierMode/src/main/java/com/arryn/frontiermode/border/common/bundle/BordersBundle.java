package com.arryn.frontiermode.border.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.fixture.NavigatorFixture;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.BorderPregenFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

import java.util.Optional;

/**
 * World-scoped bundle hosting {@link BordersFixture} and, per RM_FRO_026 ("Dorothy"), its sibling
 * {@link NavigatorFixture} -- see {@code BorderModule.init()} for the schema wiring and
 * wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border for the design.
 *
 * <p>This bundle is intentionally boring: no logic, no state beyond fixtures.
 */
public final class BordersBundle extends SatchelBundle {

    public BordersBundle(
            SatchelScope scope,
            BundleKey<BordersBundle> key
    ) {
        super(scope, key);
    }

    /**
     * SAT_053: Border state must survive a restart (see BorderModule's LevelJigConfig --
     * capabilities/persistence policy declared there), so this bundle declares the matching
     * requirement itself; the engine now gates promotion on this, not on the jig-level policy
     * alone. NETWORKING added (FRO_115): without it the client-side bundle never receives a
     * parcel and stays stuck CREATED, so cachedBorders is always empty and rings never render.
     */
    @Override
    public int required() {
        return Capability.PERSISTENCE.bit() | Capability.NETWORKING.bit();
    }

    public Optional<NavigatorFixture> navigator() {
        return get(FrontierKeys.NAVIGATOR);
    }

    public Optional<BorderCurveFixture> curve() {
        return get(FrontierKeys.CURVE);
    }

    public Optional<BorderPregenFixture> pregen() {
        return get(FrontierKeys.PREGEN);
    }
}
