//package com.arryn.satchel.common.fixture;
//
//import com.arryn.satchel.common.identity.FixtureKey;
//import com.arryn.satchel.common.newstuff.FixtureHydrationSource;
//import com.arryn.satchel.common.util.out.OUT;
//import net.minecraft.nbt.CompoundTag;
//
//import java.util.UUID;
//import java.util.function.Supplier;
//
//public final class NbtFixtureHydrationSource implements FixtureHydrationSource {
//
//    private final CompoundTag root;
//
//    public NbtFixtureHydrationSource(CompoundTag root) {
//        this.root = root;
//    }
//
//    @Override
//    public boolean hydrate(SatchelFixture facet) {
//        FixtureKey<?> key = facet.key();
//
//        CompoundTag entry = root.getCompound(key.id.toString());
//        if (entry == null) return false;
//
//        validate(entry, key);
//        facet.hydrateFromNBT(entry.getCompound("data"));
//        OUT.TRACE().log("[NbtFixtureHydrationSource] Hydrated facet " + facet.debugName());
//        return true;
//    }
//
//    @Override
//    public <T extends SatchelFixture> T hydrate(
//            FixtureKey<T> key,
//            Supplier<? extends T> factory
//    ) {
//        CompoundTag entry = root.getCompound(key.id.toString());
//        if (entry == null) {
//            return factory.get();
//        }
//
//        validate(entry, key);
//
//        T facet = factory.get();
//        facet.hydrateFromNBT(entry.getCompound("data"));
//        return facet;
//    }
//
//    private void validate(CompoundTag entry, FixtureKey<?> key) {
//        UUID id = entry.getUUID("id");
//        String name = entry.getString("name");
//
//        if (!key.id.equals(id)) {
//            throw new IllegalStateException("FixtureKey UUID mismatch for " + key);
//        }
//        if (!key.name.equals(name)) {
//            throw new IllegalStateException("FixtureKey name mismatch for " + key);
//        }
//    }
//}
