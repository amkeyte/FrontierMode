package com.arryn.satchel.common.persistence;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.FixtureKey;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

public final class ParcelHydrationSource implements FixtureHydrationSource {

    private final FixtureHydrationSource delegate;

    public ParcelHydrationSource(Map<FixtureKey<?>, CompoundTag> entriesByKey) {
        Satchel.requireClient(); // server may support later

        CompoundTag root = new CompoundTag();
        for (var e : entriesByKey.entrySet()) {
            FixtureKey<?> key = e.getKey();
            CompoundTag entry = e.getValue();
            if (entry != null) {
                root.put(key.id.toString(), entry);
            }
        }

        this.delegate = new NbtFixtureHydrationSource(root);
    }

    @Override
    public boolean hydrate(com.arryn.satchel.common.fixture.SatchelFixture facet) {
        return delegate.hydrate(facet);
    }

    @Override
    public <T extends com.arryn.satchel.common.fixture.SatchelFixture> T hydrate(
            com.arryn.satchel.common.identity.FixtureKey<T> key,
            java.util.function.Supplier<? extends T> factory
    ) {
        return delegate.hydrate(key, factory);
    }
}
