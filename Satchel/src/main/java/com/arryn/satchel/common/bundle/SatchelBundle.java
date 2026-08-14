package com.arryn.satchel.common.bundle;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.persistence.FixtureHydrationSource;
import com.arryn.satchel.common.persistence.FixtureHydrator;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.persistence.NbtFixtureHydrationSource;
import com.arryn.satchel.common.stitch.LogicalSideStitch;
import net.minecraft.nbt.CompoundTag;

import java.util.*;
import java.util.function.Supplier;

public class SatchelBundle {

    // ---------------------------------------------------------------------
    // Identity & scopeLifecycle
    // ---------------------------------------------------------------------

    private static final int DEFAULT_SYNC_INTERVAL_TICKS = 20 * 5;
    protected final SatchelScope scope;
    protected final BundleKey<?> key;

    // ---------------------------------------------------------------------
    // Bundle Lifecycle
    // ---------------------------------------------------------------------
    private final LifecycleGuard lifecycle =
            new LifecycleGuard(LifecycleState.CONSTRUCTED);

    public final LifecycleState lifeCycleState() {
        return lifecycle.state();
    }

    // ---------------------------------------------------------------------
    // Dirty / sync state
    // ---------------------------------------------------------------------
    private final Map<FixtureKey<?>, SatchelFixture> fixtures =
            new LinkedHashMap<>();
    protected boolean dirty;

    private BundleSyncDelegate syncDelegate;
    private int syncIntervalTicks = DEFAULT_SYNC_INTERVAL_TICKS;
    private int syncCounter;

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------
    private boolean hydrated;

    public SatchelBundle(SatchelScope scope, BundleKey<?> key) {
        this.scope = Objects.requireNonNull(scope, "scopeInfo");
        this.key = Objects.requireNonNull(key, "key");
    }

    public final BundleKey<?> key() {
        return key;
    }

    // ---------------------------------------------------------------------
    // Facet access
    // ---------------------------------------------------------------------

    public final SatchelScope scope() {
        return scope;
    }

    public final <T extends SatchelFixture> Optional<T> get(FixtureKey<T> key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(key.type.cast(fixtures.get(key)));
    }

    /**
     * getOrCreate throws on fail.
     *
     * @param key     the associated fixture key
     * @param factory
     * @param <T>
     * @return
     */
    public final <T extends SatchelFixture> T getOrCreateFixture(
            FixtureKey<T> key,
            Supplier<? extends T> factory
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(factory, "factory");

        Optional<T> existingOpt = get(key);
        if (existingOpt.isPresent()) return existingOpt.get();

        try {


            lifecycle.requireAny(EnumSet.of(
                    LifecycleState.CONSTRUCTED,
                    LifecycleState.CREATED
            ));


            T fixture = factory.get();
            if (fixture == null) {
                throw new IllegalStateException(
                        "Facet factory returned null for " + key
                );
            }

            fixture.attach(this);
            fixtures.put(key, fixture);
            return fixture;
        } catch (Exception e) {
            throw new IllegalStateException("Bundle was unable to get or create the requested fixture."
                    + " Bundle: " + debugName() + " : " + key.name, e);
        }

    }

    public final boolean hasFixture(FixtureKey<?> key) {
        return fixtures.containsKey(key);
    }

    public final void removeFixture(FixtureKey<?> key) {
        if (fixtures.remove(key) != null) {
            markDirty();
        }
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    public final Collection<SatchelFixture> allFixtures() {
        return Collections.unmodifiableCollection(fixtures.values());
    }

    public final CompoundTag saveAll() {
        lifecycle.requireAny(EnumSet.of(
                LifecycleState.LOADED,
                LifecycleState.ACTIVE
        ));

        CompoundTag root = new CompoundTag();

        for (Map.Entry<FixtureKey<?>, SatchelFixture> e : fixtures.entrySet()) {
            FixtureKey<?> key = e.getKey();
            SatchelFixture fixture = e.getValue();

            CompoundTag data = fixture.saveToNBT();
            if (data == null) continue;

            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", key.id);
            entry.putString("name", key.name);
            entry.put("data", data);

            root.put(key.id.toString(), entry);
        }

        return root;
    }

    public final void hydrateAll(CompoundTag root) {
        if (root == null) return;
        hydrateFrom(new NbtFixtureHydrationSource(root));
    }

    /**
     * Transitions CREATED -> HYDRATED and applies {@code source} to every existing fixture, then
     * marks this bundle hydrated. Shared by both hydration entry points: client parcel
     * application ({@link #hydrateAll}, source is always NBT) and server SavedData ingress
     * (ScopeEngine_Server.hydrateBundle, source may be real saved data OR an explicitly empty
     * source when nothing has ever been persisted yet -- either way, the bundle must leave
     * CREATED to ever reach ACTIVE, since saveAll()/onJigTick() both require it). Before this
     * method existed, ScopeEngine_Server.hydrateBundle() called FixtureHydrator directly, which
     * by its own doc "performs no lifecycle transitions" -- so no server bundle, with or without
     * existing data, ever actually left CREATED. See SAT_027.
     */
    public final void hydrateFrom(FixtureHydrationSource source) {
        lifecycle.transition(
                LifecycleState.CREATED,
                LifecycleState.HYDRATED
        );

        new FixtureHydrator(this, source).hydrateExisting();
        hydrated = true;
    }

    /**
     * Applies {@code source} to every existing fixture WITHOUT any lifecycle transition --
     * for refreshing an already-active bundle with a newer sync snapshot. {@link #hydrateFrom}
     * is one-time-only by design (its CREATED -> HYDRATED transition throws on any later call,
     * since it's meant to fire exactly once, alongside {@code onLoaded()}); every parcel after
     * the first that arrives for an already-loaded client bundle needs a way to apply updated
     * data without repeating that transition or re-firing the loaded event. Required state
     * mirrors {@link #saveAll}'s own requirement, since both operate on a bundle already
     * considered "live." See SAT_030.
     */
    public final void refreshFrom(FixtureHydrationSource source) {
        lifecycle.requireAny(EnumSet.of(
                LifecycleState.LOADED,
                LifecycleState.ACTIVE
        ));

        new FixtureHydrator(this, source).hydrateExisting();
    }
    // ---------------------------------------------------------------------
    // Lifecycle hooks (backend-controlled)
    // ---------------------------------------------------------------------

    public void onCreated() {
        lifecycle.transition(
                LifecycleState.CONSTRUCTED,
                LifecycleState.CREATED
        );

        for (SatchelFixture fixture : fixtures.values()) {
            fixture.onCreated();
        }

        markDirty();
    }

    public void onLoaded() {
        lifecycle.transition(
                LifecycleState.HYDRATED,
                LifecycleState.LOADED
        );

        for (SatchelFixture fixture : fixtures.values()) {
            fixture.onLoaded();
        }

        lifecycle.transition(
                LifecycleState.LOADED,
                LifecycleState.ACTIVE);
    }

    public void onJigTick() {
        lifecycle.require(LifecycleState.ACTIVE);

        for (SatchelFixture fixture : fixtures.values()) {
            fixture.onJigTick();
        }
    }

    public void onDestroyed() {
        if (lifecycle.isTerminal()) {
            throw new IllegalStateException("Bundle already destroyed");
        }

        lifecycle.force(LifecycleState.DESTROYING);

        for (SatchelFixture fixture : fixtures.values()) {
            fixture.onRemoved();
        }

        lifecycle.force(LifecycleState.DESTROYED);
    }

    // ---------------------------------------------------------------------
    // Dirty / sync
    // ---------------------------------------------------------------------

    public final boolean isDirty() {
        return dirty;
    }

    public final void markDirty() {
        lifecycle.requireAny(EnumSet.of(
                LifecycleState.CREATED,
                LifecycleState.HYDRATED,
                LifecycleState.LOADED,
                LifecycleState.ACTIVE
        ));
        dirty = true;
    }

    public final void clearDirty() {
        dirty = false;
    }

    public final void attachSyncDelegate(BundleSyncDelegate delegate) {
        this.syncDelegate = delegate;
    }

    public final void pulseSync(ScopeInfo info) {
        if (syncDelegate == null) return;

        syncCounter++;
        if (syncCounter < syncIntervalTicks) return;

        syncCounter = 0;
        syncDelegate.sync(info, key, this);
    }

    // ---------------------------------------------------------------------
    // Debug
    // ---------------------------------------------------------------------

    public final boolean isHydrated() {
        return hydrated;
    }

    public final FixtureKey<?> getKeyFor(SatchelFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");

        for (Map.Entry<FixtureKey<?>, SatchelFixture> e : fixtures.entrySet()) {
            if (e.getValue() == fixture) {
                return e.getKey();
            }
        }

        throw new IllegalStateException(
                "fixture instance not owned by this bundle: " + fixture
        );
    }

    protected String sideString() {
        return (scope instanceof LogicalSideStitch s && s.isServerSide())
                ? "SERVER"
                : "CLIENT";
    }

    public String debugName() {
        return getClass().getSimpleName()
                + "[" + scope.debugName() + "]"
                + "@" + System.identityHashCode(this);
    }
}
