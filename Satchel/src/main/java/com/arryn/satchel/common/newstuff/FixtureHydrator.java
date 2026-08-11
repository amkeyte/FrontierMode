package com.arryn.satchel.common.newstuff;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.stitch.CreateAtHydrateStitch;

import java.util.function.Supplier;

/**
 * Coordinates ingress hydration for a bundle.
 *
 * <p>
 * This class performs no lifecycle transitions and enforces no
 * correctness policy. It only executes hydration according to
 * capabilities exposed by the bundle.
 * </p>
 */
public final class FixtureHydrator {

    private final SatchelBundle bundle;
    private final FixtureHydrationSource source;
    private final boolean allowCreate;

    public FixtureHydrator(SatchelBundle bundle, FixtureHydrationSource source) {
        this.bundle = bundle;
        this.source = source;
        this.allowCreate =
                bundle instanceof CreateAtHydrateStitch cap
                        && cap.allowCreateAtHydrate();
    }

    /**
     * Hydrates all fixtures already present in the bundle.
     */
    public void hydrateExisting() {
        for (SatchelFixture fixture : bundle.allFixtures()) {
            source.hydrate(fixture);
        }
    }

    /**
     * Attempts to hydrate the given fixture, optionally creating it
     * if shown by bundle capability.
     *
     * @return the hydrated (or existing) fixture, or {@code null}
     *         if creation is not permitted and the fixture does not exist
     */
    public <T extends SatchelFixture> T hydrateAllowingCreate(
            FixtureKey<T> key,
            Supplier<? extends T> factory
    ) {
        if (!allowCreate) {
            return bundle.get(key).orElse(null);
        }

        return source.hydrate(key, factory);
    }
}
