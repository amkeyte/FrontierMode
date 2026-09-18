package com.arryn.satchel.common.bundle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.persistence.FixtureHydrationSource;
import com.arryn.satchel.common.persistence.FixtureHydrator;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.persistence.NbtFixtureHydrationSource;
import com.arryn.satchel.common.stitch.LogicalSideStitch;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;

import java.util.*;
import java.util.function.Supplier;

/**
 * Per-scope, per-key persisted state container -- the "Bundle" touch point named in
 * SAT_053's ownership split. Owns its own {@link LifecycleState} state machine and fans
 * every lifecycle event out to its fixtures ({@link #onCreated}/{@link #onLoaded}/
 * {@link #onJigTick}/{@link #onDestroyed} below); it does not know or care *why* it's
 * being promoted through that state machine at any given moment -- that decision belongs
 * entirely to the engine, driven by whatever this bundle declares via {@link #required()}.
 *
 * <p>
 * Extend this directly to add bundle-specific fixtures and state; override the lifecycle
 * hooks to run bundle-level setup/teardown once per phase -- each hook already fans the
 * same event out to every attached {@link SatchelFixture}, so call {@code super} first if
 * you override one, the same way every existing override in the tree does. Declare
 * {@link #required()} if this bundle actually needs persistence and/or networking to be
 * usable; the default ({@link Capability#NONE}) promotes straight to ACTIVE with no wait
 * at all (see SAT_053/SAT_049).
 */
public class SatchelBundle {

    // ---------------------------------------------------------------------
    // Identity & scopeLifecycle
    // ---------------------------------------------------------------------

    private static final int DEFAULT_SYNC_INTERVAL_TICKS = 20 * 5;

    // RM_SAT_013: grace window before the runtime half of the silent-inertness health-check
    // logs a warning. 10s is deliberately longer than DEFAULT_SYNC_INTERVAL_TICKS (5s) so a
    // bundle that's simply waiting on its first regular sync interval, per the still-open
    // proactive-push question this node's log covers, doesn't trip the warning under normal
    // conditions.
    private static final int INERTNESS_WARNING_TICKS = 20 * 10;

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

    private BundleFlushDelegate flushDelegate;

    private int ticksNotActive;
    private boolean inertnessWarned;

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
     * Applies {@code source} to every existing fixture and marks this bundle hydrated, without
     * itself performing any lifecycle transition -- promotion to LOADED/ACTIVE is the caller's
     * job (see {@link #onLoaded}), driven by whatever capability axes the bundle actually
     * declares via {@link #required()}. Only callable in CREATED, and only meaningful once: a
     * bundle whose {@link #required()} doesn't include {@link Capability#PERSISTENCE} should
     * never call this at all and can be promoted straight from CREATED. Shared by both hydration
     * entry points: client parcel application ({@link #hydrateAll}, source is always NBT) and
     * server SavedData ingress (ScopeEngine_Server.hydrateBundle, source may be real saved data
     * OR an explicitly empty source when nothing has ever been persisted yet). Before HYDRATED
     * was removed from the state machine (SAT_053), this method's own transition was what a
     * bundle needing neither persistence nor networking could never reach -- see SAT_049.
     */
    public final void hydrateFrom(FixtureHydrationSource source) {
        lifecycle.require(LifecycleState.CREATED);

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

    /**
     * Fired once, immediately after construction: transitions CONSTRUCTED -> CREATED,
     * then fans {@code onCreated()} out to every attached fixture. Override to run
     * bundle-level one-time setup -- call {@code super.onCreated()} first so the
     * transition and fixture fan-out still happen.
     */
    public void onCreated() {
        lifecycle.transition(
                LifecycleState.CONSTRUCTED,
                LifecycleState.CREATED
        );

        for (SatchelFixture fixture : fixtures.values()) {
            fixture.onCreated();
        }

        if (Satchel.isServer()) {

            markDirty();
        }
    }

    /**
     * Fired once this bundle's declared {@link #required()} capabilities are satisfied
     * (immediately, if it declares none): transitions CREATED -> LOADED, fans {@code
     * onLoaded()} out to every fixture -- this is what flips each fixture's own {@code
     * isReady()} to true, see {@link SatchelFixture} -- then transitions LOADED -> ACTIVE.
     * Override to run bundle-level setup that depends on hydrated fixture data being in
     * place -- call {@code super.onLoaded()} first, the same way every existing override
     * in the tree does.
     */
    public void onLoaded() {
        lifecycle.transition(
                LifecycleState.CREATED,
                LifecycleState.LOADED
        );

        for (SatchelFixture fixture : fixtures.values()) {
            fixture.onLoaded();
        }

        lifecycle.transition(
                LifecycleState.LOADED,
                LifecycleState.ACTIVE);
    }

    /**
     * Fired once per jig tick while this bundle is ACTIVE (requires ACTIVE, throws
     * otherwise): fans {@code onJigTick()} out to every attached fixture. Override to run
     * bundle-level per-tick logic -- call {@code super.onJigTick()} first so fixtures
     * still tick.
     */
    public void onJigTick() {
        lifecycle.require(LifecycleState.ACTIVE);

        for (SatchelFixture fixture : fixtures.values()) {
            fixture.onJigTick();
        }
    }

    /**
     * Fired once, when this bundle is being torn down: forces DESTROYING, fans {@code
     * onRemoved()} out to every attached fixture, then forces DESTROYED. Override to run
     * bundle-level teardown -- call {@code super.onDestroyed()} first so fixtures still
     * get their {@code onRemoved()}.
     */
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

    public final void attachFlushDelegate(BundleFlushDelegate delegate) {
        this.flushDelegate = delegate;
    }

    /**
     * SAT_068: forces this bundle's current state to disk immediately, bypassing whatever
     * {@code FlushPolicy} cadence it's otherwise governed by (including {@code NONE}) --
     * for a caller that considers this bundle's current state too important to leave to the
     * next automatic flush or Minecraft's own autosave cycle (a boss defeat, say). Not
     * gated on {@link #isDirty()}: always re-serializes and writes the bundle's current state,
     * since the caller's whole point is "make sure what's here right now is durable," not
     * "only if something happens to think it's stale."
     *
     * <p>No-op if never attached -- true client-side only, mirroring {@link #pulseSync}'s own
     * null-delegate handling (no persistence engine exists to flush to). Server-side, the
     * delegate is always attached at creation regardless of what this bundle declares; calling
     * this on a bundle that never declared {@link Capability#PERSISTENCE} is a caller mistake,
     * not a quiet no-op, and the attached delegate throws for that case rather than silently
     * doing nothing.
     */
    public final void forceWriteToDisk() {
        if (flushDelegate == null) return;
        flushDelegate.flush();
    }

    /**
     * RM_SAT_013: runtime half of the silent-inertness health-check. Called once per execution
     * pulse by {@code ScopeEngine_Server}/{@code ScopeEngine_Client}, alongside
     * {@link #pulseSync}. Diagnostic only, never throws: logs a single warning if this bundle has
     * sat below {@code ACTIVE} for longer than a short grace window, then stays quiet -- resets
     * the counter the moment {@code ACTIVE} is reached, and only ever warns once per bundle so a
     * genuinely slow (but not actually broken) path, like a client bundle waiting on a delayed
     * first parcel, doesn't spam the log every pulse afterward.
     */
    public final void healthCheckPulse() {
        if (lifeCycleState() == LifecycleState.ACTIVE) {
            ticksNotActive = 0;
            return;
        }

        ticksNotActive++;
        if (!inertnessWarned && ticksNotActive >= INERTNESS_WARNING_TICKS) {
            inertnessWarned = true;
            OUT.warn(
                    "[health-check] Bundle " + debugName() + " has not reached ACTIVE after "
                            + ticksNotActive + " execution pulses (still " + lifeCycleState()
                            + "). Possible silent inertness -- see RM_SAT_013."
            );
        }
    }

    // ---------------------------------------------------------------------
    // Debug
    // ---------------------------------------------------------------------

    /**
     * Bitmask (see {@link Capability}) of the capability axes this bundle actually needs
     * satisfied before it can be promoted out of CREATED. Declared by the bundle author --
     * override per subclass. Defaults to {@link Capability#NONE}: a bundle that declares no
     * requirements is promoted the moment it's created, with no hydration/network wait at all.
     * See SAT_053 / SAT_049.
     */
    public int required() {
        return Capability.NONE;
    }

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
