package com.arryn.satchel.common.fixture;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.throttle.TickThrottler;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.*;

/**
 * Base class for Satchel 2.0 fixtures.
 *
 * Fixtures are deterministic, state-only units that live containing a SatchelBundle.
 */
public abstract class SatchelFixture {

    private long revision = 0;
    public final long revision() {return revision;}
    // ---------------------------------------------------------------------
    // Bundle wiring
    // ---------------------------------------------------------------------

    private SatchelBundle bundle;

    protected SatchelFixture(){
        initTrace();

        registerLong(
                "__revision",
                () -> revision,
                v -> revision = v
        );
    }

    private void initTrace(){
        OUT.TRACE().registerCaller(
                this,
                "isReady",
                new TickThrottler.AutoClock(),
                1000L);
        OUT.TRACE().registerCaller(
                this,
                "isnotready",
                new TickThrottler.AutoClock(),
                1000L);
    }


    /**
     * Called exactly once by SatchelBundle during facet construction.
     */
    public final void attach(SatchelBundle bundle) {
        if (this.bundle != null) {
            throw new IllegalStateException(
                    "Fixture already bound to bundle: " + getClass().getName()
            );
        }
        this.bundle = bundle;
    }

    public final FixtureKey<?> key(){
        return bundle.getKeyFor(this);
    }

    //WALKS UP scopeInfo TREE
    @SuppressWarnings("unchecked")
    public final <T extends SatchelBundle> T getBundle() {
        return (T) bundle;
    }

    public final SatchelScope scope() {
        return bundle.scope();
    }

    // ---------------------------------------------------------------------
    // Dirty propagation (runtime only)
    // ---------------------------------------------------------------------

    /**
     * Mark the parent bundle dirty.
     *
     * Must only be called during runtime mutation, never during hydrate.
     */
    protected final void markDirty() {
        revision++;
        if (bundle != null) {
            bundle.markDirty();
        }
    }

    // ---------------------------------------------------------------------
    // Lifecycle hooks
    // ---------------------------------------------------------------------

    public boolean isReady(){
        if(ready){
            OUT.TRACE().log(this,"isready","[SatchelFixture] is ready: " + debugName());
        }else{
            OUT.TRACE().log(this,"isnotready","[SatchelFixture] is NOT ready:" + debugName());
        }
        return  ready;
    }
    private boolean ready = false;
    public void onCreated() {}
    public void onLoaded() {
        OUT.TRACE().log("[SatchelFixture] has loaded:" + debugName());

        ready = true;
    }
    public void onRemoved() {ready = false;}
    public void onJigTick() {}
    // ---------------------------------------------------------------------
    // Serialization
    // ---------------------------------------------------------------------

    /**
     * Serialize registered fields.
     * Override only for custom encoding.
     */
    protected void save(CompoundTag tag) {
        for (INBTField field : fields.values()) {
            field.save(tag);
        }
    }

    /**
     * Deserialize registered fields.
     * Override only for custom decoding.
     */
    protected void hydrate(CompoundTag tag) {
        for (INBTField field : fields.values()) {
            field.hydrate(tag);
        }
    }

    // ---------------------------------------------------------------------
    // Canonical SatchelBundle API (FINAL)
    // ---------------------------------------------------------------------

    public final CompoundTag saveToNBT() {
        CompoundTag tag = new CompoundTag();
        save(tag);
        return tag;
    }

    public final void hydrateFromNBT(CompoundTag tag) {
        if (tag != null) {
            hydrate(tag);
        }
    }

    // ---------------------------------------------------------------------
    // Field registration helpers
    // ---------------------------------------------------------------------

    private interface INBTField {
        void save(CompoundTag tag);
        void hydrate(CompoundTag tag);
    }

    private final Map<String, INBTField> fields = new LinkedHashMap<>();

    protected final void registerInt(
            String name,
            IntSupplier getter,
            IntConsumer setter)
    {
        if (fields.containsKey(name)) {
            throw new IllegalStateException("Duplicate facet field: " + name);
        }
        fields.put(name, new INBTField() {
            @Override
            public void save(CompoundTag tag) {
                tag.putInt(name, getter.getAsInt());
            }

            @Override
            public void hydrate(CompoundTag tag) {
                if (tag.contains(name)) {
                    setter.accept(tag.getInt(name));
                }
            }
        });
    }
    protected final void registerLong(
            String name,
            LongSupplier getter,
            LongConsumer setter)
    {
        if (fields.containsKey(name)) {
            throw new IllegalStateException("Duplicate facet field: " + name);
        }
        fields.put(name, new INBTField() {
            @Override
            public void save(CompoundTag tag) {
                tag.putLong(name, getter.getAsLong());
            }

            @Override
            public void hydrate(CompoundTag tag) {
                if (tag.contains(name)) {
                    setter.accept(tag.getLong(name));
                }
            }
        });
    }

    protected final void registerUUID(
            String name,
            Supplier<UUID> getter,
            Consumer<UUID> setter)
    {
        if (fields.containsKey(name)) {
            throw new IllegalStateException("Duplicate facet field: " + name);
        }
        fields.put(name, new INBTField() {
            @Override
            public void save(CompoundTag tag) {
                UUID value = getter.get();
                if (value != null) {
                    tag.putUUID(name, value);
                }
            }

            @Override
            public void hydrate(CompoundTag tag) {
                if (tag.hasUUID(name)) {
                    setter.accept(tag.getUUID(name));
                }
            }
        });
    }

    // In SatchelFixture


    protected final void registerCustom(
            String name,
            Consumer<CompoundTag> saver,
            Consumer<CompoundTag> loader
    ) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(saver, "saver");
        Objects.requireNonNull(loader, "loader");

        if (fields.containsKey(name)) {
            throw new IllegalStateException("Duplicate facet field: " + name);
        }

        fields.put(name, new INBTField() {
            @Override
            public void save(CompoundTag root) {
                saver.accept(root);
            }

            @Override
            public void hydrate(CompoundTag root) {
                loader.accept(root);
            }
        });
    }
    public String debugName() {
        return this.getClass().getSimpleName()
                + "[" + bundle.debugName() + "]"
                + "@" + System.identityHashCode(this);
    }
}
