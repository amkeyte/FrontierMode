package com.arryn.satchel.common.persistence;

import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.FixtureKey;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fixture hydration source backed by a bundle-style NBT root.
 *
 * <p>
 * This source does not enforce sidedness; callers are responsible
 * for constructing it only in valid contexts (server persistence,
 * parcel ingress, tests, etc).
 * </p>
 */
public final class NbtFixtureHydrationSource
        implements FixtureHydrationSource {

    private final CompoundTag root;

    public NbtFixtureHydrationSource(CompoundTag root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /**
     * Attempts to hydrate an already-existing fixture instance.
     *
     * @return true if hydration data was found and applied
     */
    @Override
    public boolean hydrate(SatchelFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");

        FixtureKey<?> key = fixture.key();
        String id = key.id.toString();

        if (!root.contains(id, CompoundTag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag entry = root.getCompound(id);

        if (!entry.contains("data", CompoundTag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag data = entry.getCompound("data");
        fixture.hydrateFromNBT(data);
        return true;
    }

    /**
     * Gets or creates a fixture and hydrates it if data is present.
     *
     * <p>
     * Creation is delegated to the bundle via the supplied factory.
     * </p>
     */
    @Override
    public <T extends SatchelFixture> T hydrate(
            FixtureKey<T> key,
            Supplier<? extends T> factory
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(factory, "factory");

        String id = key.id.toString();

        if (!root.contains(id, CompoundTag.TAG_COMPOUND)) {
            return factory.get();
        }

        CompoundTag entry = root.getCompound(id);

        if (!entry.contains("data", CompoundTag.TAG_COMPOUND)) {
            return factory.get();
        }

        CompoundTag data = entry.getCompound("data");

        T fixture = factory.get();
        fixture.hydrateFromNBT(data);
        return fixture;
    }
}
