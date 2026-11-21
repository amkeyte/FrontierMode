package com.arryn.satchel.common.fixture;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.SchedulerClock;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.*;

/**
 * Base class for a per-instance, deterministic unit of a {@link SatchelBundle}'s state --
 * the "Fixture" touch point named in SAT_053's ownership split. Unlike the coupler, this
 * is an established, actively-used pattern: {@link #isReady()} is the live,
 * consumer-facing "is this data trustworthy yet" signal (read by, among others,
 * FrontierMode's BorderAPI, RenderContext, BossMobFixture, and BordersInfoFacet), and it
 * is correct precisely because every real {@link #onLoaded()} override in the tree calls
 * {@code super.onLoaded()} first -- see that method's javadoc below for why that matters.
 *
 * <p>
 * A fixture is deterministic and state-only: no engine coupling, no lifecycle-transition
 * logic of its own (that belongs to the owning {@link SatchelBundle}, which fans every
 * hook below out to each attached fixture in lockstep with its own lifecycle -- see
 * {@link SatchelBundle}'s class javadoc). Register persisted fields with {@link
 * #registerInt}/{@link #registerLong}/{@link #registerUUID}/{@link #registerCustom} in
 * the constructor rather than overriding {@link #save}/{@link #hydrate} directly, unless
 * custom encoding is genuinely needed.
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
                new SchedulerClock.AutoClock(),
                1000L);
        OUT.TRACE().registerCaller(
                this,
                "isnotready",
                new SchedulerClock.AutoClock(),
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

    /**
     * Whether this fixture's data is currently trustworthy to read. The live,
     * consumer-facing readiness signal -- flips true the moment {@link #onLoaded()} runs
     * (or whatever this fixture's own override does instead, see that method's javadoc),
     * false again once {@link #onRemoved()} runs. Read directly by real consumers today
     * (FrontierMode's BorderAPI, RenderContext, BossMobFixture, BordersInfoFacet, among
     * others) -- this is the working half of Satchel's two readiness concepts; see
     * {@code ScopeCoupler#isReady} for the other, currently-unused half (scope-level
     * readiness, a different axis on a different clock).
     */
    public boolean isReady(){
        if(ready){
            OUT.TRACE().log(this,"isready","[SatchelFixture] is ready: " + debugName());
        }else{
            OUT.TRACE().log(this,"isnotready","[SatchelFixture] is NOT ready:" + debugName());
        }
        return  ready;
    }
    private boolean ready = false;

    /**
     * Fired once, when the owning bundle transitions CONSTRUCTED -> CREATED. Override for
     * one-time setup that doesn't depend on hydrated/loaded data -- {@link #isReady()} is
     * still false at this point.
     */
    public void onCreated() {}

    /**
     * Fired once, when the owning bundle transitions CREATED -> LOADED (immediately, for
     * a bundle whose {@link SatchelBundle#required()} needs nothing satisfied first). The
     * base implementation flips {@link #isReady()} to true -- <b>every override must call
     * {@code super.onLoaded()} first</b> (every real override in the tree already does),
     * or {@link #isReady()} silently stops being a trustworthy signal for this fixture.
     */
    public void onLoaded() {
        OUT.TRACE().log("[SatchelFixture] has loaded:" + debugName());

        ready = true;
    }

    /**
     * Fired once, when the owning bundle is torn down. Base implementation flips {@link
     * #isReady()} back to false. Override for teardown, calling {@code super.onRemoved()}
     * for the same reason {@link #onLoaded()} needs {@code super} called.
     */
    public void onRemoved() {ready = false;}

    /**
     * Fired once per jig tick while the owning bundle is ACTIVE. Override for per-tick
     * fixture logic; no base behavior to preserve, so calling {@code super} is optional.
     */
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
