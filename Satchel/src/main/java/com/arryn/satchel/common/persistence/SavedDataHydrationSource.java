package com.arryn.satchel.common.persistence;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Terminal hydration source backed by Forge SavedData.
 *
 * <p>
 * This class is the end-of-line for persistence ingress.
 * It is the ONLY place that knows about:
 * <ul>
 *   <li>ServerLevel</li>
 *   <li>SavedData</li>
 *   <li>Forge persistence semantics</li>
 * </ul>
 *
 * <p>
 * All sidedness is enforced at construction time.
 * Upstream code may assume correctness.
 */
public final class SavedDataHydrationSource
        implements FixtureHydrationSource {

    private final FixtureHydrationSource delegate;

    private SavedDataHydrationSource(CompoundTag root) {
        this.delegate = new NbtFixtureHydrationSource(root);
    }

    /**
     * Attempts to construct a SavedData-backed hydration source
     * for the given bundle.
     *
     * @return empty if no persisted data exists for the bundle
     */
    public static Optional<SavedDataHydrationSource> forBundle(
            ServerLevel level,
            BundleKey<?> key
    ) {
        // -----------------------------------------------------------------
        // Sidedness enforcement (terminal boundary)
        // -----------------------------------------------------------------
        Satchel.requireServer();

        BundleSavedData data =
                BundleSavedData.getForRead(level, key);

        if (data == null) {
            return Optional.empty();
        }

        return Optional.of(
                new SavedDataHydrationSource(
                        data.getFixtureSnapshot()
                )
        );
    }

    // ---------------------------------------------------------------------
    // FixtureHydrationSource delegation
    // ---------------------------------------------------------------------

    @Override
    public boolean hydrate(
            com.arryn.satchel.common.fixture.SatchelFixture fixture
    ) {
        return delegate.hydrate(fixture);
    }

    @Override
    public <T extends com.arryn.satchel.common.fixture.SatchelFixture> T hydrate(
            com.arryn.satchel.common.identity.FixtureKey<T> key,
            java.util.function.Supplier<? extends T> factory
    ) {
        return delegate.hydrate(key, factory);
    }
}
