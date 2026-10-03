package com.arryn.satchel.server.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.LifecycleState;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.*;
import com.arryn.satchel.common.persistence.NbtFixtureHydrationSource;
import com.arryn.satchel.common.persistence.ParcelEgressSink;
import com.arryn.satchel.common.persistence.SavedDataEgressSink;
import com.arryn.satchel.common.persistence.SavedDataHydrationSource;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Server-side backend execution engine.
 *
 * Responsibilities:
 * - bundle resolution + creation
 * - persistence hydration (STATE ONLY)
 * - dirty flushing
 * - sync scheduling
 *
 * Notes:
 * - keyed by scope UUID (value identity)
 * - never returns null bundles
 */
public final class ScopeEngine_Server implements ScopeEngine {

    // scope UUID -> (BundleKey -> bundle)
    private final Map<UUID, Map<BundleKey<?>, SatchelBundle>> active =
            new HashMap<>();

    private final Map<BundleKey<?>, JigBundles.BundleDecl<?, ?>> bundleDecls =
            new LinkedHashMap<>();

    private boolean schemaFrozen = false;

    // ---------------------------------------------------------------------
    // CONFIG (live category references only)
    // ---------------------------------------------------------------------

    private JigBindingConfig<?,?> binding;
    private JigExecutionConfig execution;
    private JigPoliciesConfig policies;
    private JigBundlesConfig<?> bundles;

    @Override
    public void installJigConfig(CompiledJigConfig config) {
        Objects.requireNonNull(config, "config");

        binding = config.binding();
        execution = config.execution();
        policies = config.policies();
        bundles = config.bundles();

        // IMPORTANT:
        // Do NOT auto-register schema here.
        // Schema intake is controlled via registerBundleSchema(...) + freezeBundleSchema()
        // so the coupler/builder can determine timing and avoid duplicates.
    }

    private void requireConfig() {
        if (binding == null) {
            throw new IllegalStateException("JigConfig not installed: " + debugName());
        }
    }

    @Override public JigBindingConfig<?, ?> binding() { return binding; }
    @Override public JigExecutionConfig execution() { return execution; }
    @Override public JigPoliciesConfig policies() { return policies; }
    @Override public JigBundlesConfig<?> bundles() { return bundles; }

    /* ------------------------------------------------------------
     * Bundle schema intake (INERT)
     * --------------------------------------------------------- */

    @Override
    public void registerBundleSchema(JigBundles.Schema<?> schema) {
        Objects.requireNonNull(schema, "schema");

        if (schemaFrozen) {
            throw new IllegalStateException(
                    "Cannot register bundle schema after freeze"
            );
        }

        for (JigBundles.BundleDecl<?, ?> decl : schema.bundles()) {
            BundleKey<?> key = decl.key();

            if (bundleDecls.containsKey(key)) {
                throw new IllegalStateException(
                        "Duplicate BundleKey registered: " + key.name
                );
            }

            bundleDecls.put(key, decl);
        }
    }

    @Override
    public void freezeBundleSchema() {
        schemaFrozen = true;
    }

    /**
     * double-triple check that the info, scope, or id are not somehow null.
     * required to ensure no strange mapping access.
     */
    private UUID validatedScopeId(ScopeInfo info) {
        Objects.requireNonNull(info, "info");
        SatchelScope scope = Objects.requireNonNull(info.scope(), "scope");
        return Objects.requireNonNull(scope.uuid(), "scope.uuid()");
    }

    private Map<BundleKey<?>, SatchelBundle> bundlesFor(ScopeInfo info) {
        return active.computeIfAbsent(validatedScopeId(info), id -> new HashMap<>());
    }

    /* =============================================================
     * Bundle access
     * ========================================================== */

    @Override
    @SuppressWarnings("unchecked")
    public <B extends SatchelBundle> Optional<B> ask(ScopeInfo info, BundleKey<B> key) {
        if (info == null || key == null) return Optional.empty();

        Map<BundleKey<?>, SatchelBundle> map = bundlesFor(info);
        if (map == null) return Optional.empty();

        return Optional.ofNullable((B) map.get(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <B extends SatchelBundle> B get(ScopeInfo info, BundleKey<B> key) {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(key, "key");

        Map<BundleKey<?>, SatchelBundle> map = bundlesFor(info);
        if (map == null) {
            throw new SatchelException.BundleNotFound(
                    "No bundles registered for scope: " + info.debugName()
            );
        }

        SatchelBundle b = map.get(key);
        if (b == null) {
            throw new SatchelException.BundleNotFound(
                    "Bundle not present: scope=" + info.debugName() + " key=" + key.name
            );
        }

        return (B) b;
    }

    @Override
    public <B extends SatchelBundle> B create(ScopeInfo info, BundleKey<B> key) {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(key, "key");

        // If already present, return existing (idempotent).
        Optional<B> existing = ask(info, key);
        if (existing.isPresent()) return existing.get();

        @SuppressWarnings("unchecked")
        JigBundles.BundleDecl<SatchelScope, B> decl =
                (JigBundles.BundleDecl<SatchelScope, B>) bundleDecls.get(key);
        if (decl == null) {
            throw new IllegalStateException("No BundleDecl registered for " + key);
        }

        SatchelScope scope = Objects.requireNonNull(info.scope(), "scope");

        B bundle = Objects.requireNonNull(
                decl.factory().create(scope),
                "Bundle factory returned null for " + key
        );

        OUT.TRACE().log("[engine] SERVER Bundle Created: " + bundle.debugName());
        OUT.TRACE().log("         using " + key);
        OUT.TRACE().log("         in scope " + info.debugName());

        // Define bundle shape immediately
        for (JigBundles.FixtureDecl<?> fixtureDecl : decl.fixtures()) {
            applyFixture(bundle, fixtureDecl);
        }

        // Register before hydration so hooks can resolve it if needed
        bundlesFor(info).put(key, bundle);

        // Sync & lifecycle hooks
        bundle.attachSyncDelegate(this::scheduleSync);
        // SAT_068: info/key bound in now since they don't change for this bundle's lifetime --
        // forceWriteToDisk() stays a zero-arg call for module code that only ever holds the
        // bundle reference, never a ScopeInfo.
        bundle.attachFlushDelegate(() -> forceFlushToDisk(info, key, bundle));
        // SAT_081: same binding-at-create shape as the flush delegate above.
        bundle.attachForceSyncDelegate(() -> forceSyncNow(info, key, bundle));
        bundle.onCreated();

        // Hydrate state-only
        hydrateBundle(info, key, bundle);

        return bundle;
    }

    /* =============================================================
     * Execution
     * ========================================================== */

    @Override
    public void onExecutionPulse(ScopeInfo info) {
        onExecutionPulse(info, false);
    }

    /**
     * @return whether any bundle was flushed into {@link BundleSavedData}'s snapshot this pass
     */
    private boolean onExecutionPulse(ScopeInfo info, boolean terminal) {
        if (info == null) return false;

        Map<BundleKey<?>, SatchelBundle> map = bundlesFor(info);
        if (map == null || map.isEmpty()) return false;

        boolean flushed = false;

        for (BundleKey<?> key : List.copyOf(map.keySet())) {
            SatchelBundle bundle = map.get(key);
            if (bundle == null) continue;

            bundle.pulseSync(info);
            bundle.healthCheckPulse();

            if (bundle.isDirty()) {
                flushed |= flushIfDirty(info, key, bundle, terminal);
            }
        }
        return flushed;
    }

    @Override
    public void onJigTick(ScopeInfo info) {
        if (info == null) return;

        Map<BundleKey<?>, SatchelBundle> map = bundlesFor(info);
        if (map == null || map.isEmpty()) return;

        for (BundleKey<?> key : List.copyOf(map.keySet())) {
            SatchelBundle bundle = map.get(key);
            if (bundle == null || bundle.lifeCycleState() != LifecycleState.ACTIVE) continue;

            bundle.onJigTick();
            Satchel.require().bundleLifecycle()
                    .signalBundleTick(info, bundle);
        }
    }

    @Override
    public void unload(ScopeInfo info) {
        if (info == null) return;

        // Terminal maintenance flush. It only refreshes the SavedData snapshot; a level unloaded
        // at server stop is unloaded after vanilla's own save pass has already run, so without an
        // explicit write here a change made at teardown (a Trinket's onDetach, say) would never
        // reach disk. Same level-wide write forceWriteToDisk() uses, done only when this pass
        // actually flushed something and the server is stopping -- while it runs, vanilla's
        // autosave covers an ordinary mob or player scope unloading, and a write per teardown
        // would be needless disk traffic.
        if (onExecutionPulse(info, true)) {
            ServerLevel level = resolveServerLevel(info, CapableOf.PERSISTENCE);
            if (!level.getServer().isRunning()) {
                level.getDataStorage().save();
            }
        }

        // RM_SAT_014, part 2: must read the map *before* evicting it below, then evict
        // regardless of whether it had anything in it. Previously this method called
        // bundle.onDestroyed() on every bundle here but never removed the scope's own entry
        // from `active` -- so the next time a scope with the same UUID was created (e.g. a new
        // world reusing the same dimension name, since LevelScope's UUID is deterministic from
        // the dimension name alone), bundlesFor()'s computeIfAbsent found the old, already
        // torn-down bundle map still sitting there and handed it back instead of building fresh
        // ones. ScopeEngine_Client.unload() already evicts correctly (active.remove(...)); this
        // brings the server engine's behavior in line with it.
        Map<BundleKey<?>, SatchelBundle> map = active.remove(validatedScopeId(info));
        if (map == null || map.isEmpty()) return;

        for (SatchelBundle bundle : map.values()) {
            try {
                Satchel.require().bundleLifecycle()
                        .signalBundleUnloaded(info, bundle);
                bundle.onDestroyed();
            } catch (Throwable t) {
                OUT.error("[engine] bundle.onDestroyed failed: " + bundle.debugName());
            }
        }
    }

    /* =============================================================
     * Hydration + facets
     * ========================================================== */

    private <B extends SatchelBundle> void hydrateBundle(
            ScopeInfo info,
            BundleKey<B> key,
            B bundle
    ) {
        // SAT_053: whether we even ATTEMPT persistence is gated on the bundle's own declared
        // requirement, not a jig-level policy flag -- a bundle that never claims
        // Capability.PERSISTENCE has nothing to wait on here at all, it promotes on the spot.
        // This is the literal SAT_049 fix: under the old jig-level-only check, such a bundle
        // never left CREATED because hydration was the sole promotion path and it was gated on
        // a capability the bundle never asked for. (resolveServerLevel() below separately
        // checks whether the JIG allows persistence at all -- a bundle requiring it against a
        // jig that doesn't allow it is a real error, not a skip.)
        if ((bundle.required() & Capability.PERSISTENCE.bit()) != 0) {
            ServerLevel level = resolveServerLevel(info, CapableOf.PERSISTENCE);

            Optional<SavedDataHydrationSource> source =
                    SavedDataHydrationSource.forBundle(level, key);

            if (source.isPresent()) {
                bundle.hydrateFrom(source.get());
            } else {
                // No saved data yet (first-ever creation -- the common case for a fresh world).
                // IGNORE/WARN still hydrate from an explicitly empty source rather than leaving
                // the bundle stuck: it still needs to reach ACTIVE, since saveAll()/onJigTick()
                // both require LOADED/ACTIVE, and "nothing to load" is a normal state on its own
                // (see SAT_027) -- missingPolicy() only changes whether that's silent, logged, or
                // (ERROR) rejected outright instead of papered over. Not a raw FixtureHydrator
                // call -- that class explicitly documents itself as performing no lifecycle
                // transitions; bundle.hydrateFrom(...) is the real entry point (see SAT_061).
                NbtFixtureHydrationSource fallback = new NbtFixtureHydrationSource(new CompoundTag());

                JigPolicies.MissingDataPolicy policy = info.policies().persistence().missingPolicy();

                switch (policy) {
                    case IGNORE:
                        bundle.hydrateFrom(fallback);
                        break;
                    case WARN:
                        OUT.warn(
                                "Missing persistence data for bundle " + key.name
                                        + " in scope " + info.debugName()
                        );
                        bundle.hydrateFrom(fallback);
                        break;
                    case ERROR:
                        throw new SatchelException.AccessFailed(
                                "Missing persistence data for bundle " + key.name
                                        + " in scope " + info.debugName()
                        );
                }
            }
        }

        // Whatever this bundle required (persistence, or nothing at all) is now satisfied --
        // promote it exactly once. hydrateBundle() only ever runs once per bundle (called from
        // create()), so the CREATED check below is just a defensive guard, not a polling loop;
        // the async/polled version of this check (for capabilities that can't be satisfied
        // synchronously, e.g. networking) is deliberately not built yet -- see SAT_053's open
        // questions.
        if (bundle.lifeCycleState() == LifecycleState.CREATED) {
            bundle.onLoaded();
            Satchel.require().bundleLifecycle()
                    .signalBundleLoaded(info, bundle);
        }
    }

    private <F extends SatchelFixture> void applyFixture(
            SatchelBundle bundle,
            JigBundles.FixtureDecl<F> decl
    ) {
        bundle.getOrCreateFixture(decl.key(), decl.factory()::create);
    }

    /* =============================================================
     * Persistence flush + sync
     * ========================================================== */

    private boolean flushIfDirty(
            ScopeInfo info,
            BundleKey<?> key,
            SatchelBundle bundle,
            boolean terminal
    ) {
        if (!bundle.isDirty()) return false;

        // SAT_053: same bundle-level gate as hydrateBundle() -- nothing to flush to if this
        // bundle never declared Capability.PERSISTENCE in the first place. resolveServerLevel()
        // below separately enforces that the jig allows it.
        if ((bundle.required() & Capability.PERSISTENCE.bit()) == 0) {
            return false;
        }

        JigPolicies.FlushPolicy flushOn = info.policies().persistence().flushOn();

        switch (flushOn) {
            case NONE:
                return false;
            case UNLOAD:
                if (!terminal) {
                    return false;
                }
                break;
            case EXECUTION_PULSE:
                // Fall through to flush
                break;
        }

        ServerLevel level = resolveServerLevel(info, CapableOf.PERSISTENCE);

        SavedDataEgressSink
                .forBundle(level, key)
                .store(bundle.saveAll());

        bundle.clearDirty();
        return true;
    }

    /**
     * SAT_068: the delegate behind {@link SatchelBundle#forceWriteToDisk()}. Diagnosed live:
     * {@code EXECUTION_PULSE}'s own {@link #flushIfDirty} only refreshes {@link BundleSavedData}'s
     * in-memory snapshot and marks vanilla's own dirty flag -- actual disk durability was still
     * entirely bounded by Minecraft's own autosave cycle (default ~5 min) or a clean shutdown, so
     * a crash inside that window still lost a "significant event" mutation (a boss kill, e.g.)
     * FRO_101's flushOn wiring alone couldn't have closed. This is a distinct, explicit,
     * caller-invoked path -- unlike {@link #flushIfDirty}, it ignores {@code flushOn} entirely
     * (including {@code NONE}) and isn't gated on {@link SatchelBundle#isDirty()}: the caller's
     * whole point is "this is durable right now," not "only if something already thinks it's
     * stale."
     *
     * <p>Unlike the automatic path, a missing {@code Capability.PERSISTENCE} declaration here
     * throws rather than silently skipping -- calling this on a bundle that was never going to be
     * written to disk at all is a caller wiring mistake, the same "crash loudly, don't paper over
     * it" precedent {@link #resolveServerLevel} already sets for a jig/bundle capability mismatch.
     */
    private void forceFlushToDisk(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle) {
        if ((bundle.required() & Capability.PERSISTENCE.bit()) == 0) {
            throw new SatchelException.AccessFailed(
                    "forceWriteToDisk() called on bundle " + bundle.debugName()
                            + ", which does not declare Capability.PERSISTENCE -- nothing to "
                            + "flush. Either this bundle needs PERSISTENCE, or this call is "
                            + "wrong for it."
            );
        }

        ServerLevel level = resolveServerLevel(info, CapableOf.PERSISTENCE);

        SavedDataEgressSink
                .forBundle(level, key)
                .store(bundle.saveAll());

        bundle.clearDirty();

        // store() above only refreshes the in-memory BundleSavedData snapshot and marks vanilla's
        // own SavedData dirty -- the same half EXECUTION_PULSE already did. This call is the
        // actual fix: DimensionDataStorage#save() synchronously writes every currently-dirty
        // SavedData for this level to disk right now, the same real, vanilla-native call
        // autosave and a clean shutdown both already use -- confirmed via the mapped MC jar
        // (DimensionDataStorage.save() -> SavedData.save(File), gated on isDirty() internally),
        // not assumed. Deliberately level-wide, not single-record: any other bundle that happens
        // to be dirty at this exact moment gets the same durability for free, which is a feature
        // here, not scope creep -- one real disk write, not N.
        level.getDataStorage().save();
    }

    /**
     * SAT_081: the delegate behind {@link SatchelBundle#forceSync()}. Runs the exact same {@link
     * #scheduleSync} the fixed timer runs -- one send path, two triggers -- so it inherits that
     * method's skip when nobody is in the dimension, and {@code SatchelNetwork.send}'s jig-level
     * {@code allowsNetworking()} gate. Unlike the timed path, a missing {@code
     * Capability.NETWORKING} declaration throws rather than silently sending: calling this on a
     * bundle that was never meant to be networked is a caller wiring mistake, the same "crash
     * loudly" precedent {@link #forceFlushToDisk} sets for a missing PERSISTENCE declaration.
     */
    private void forceSyncNow(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle) {
        if ((bundle.required() & Capability.NETWORKING.bit()) == 0) {
            throw new SatchelException.AccessFailed(
                    "forceSync() called on bundle " + bundle.debugName()
                            + ", which does not declare Capability.NETWORKING -- nothing to "
                            + "push. Either this bundle needs NETWORKING, or this call is "
                            + "wrong for it."
            );
        }
        scheduleSync(info, key, bundle);
    }

    private void scheduleSync(
            ScopeInfo info,
            BundleKey<?> key,
            SatchelBundle bundle
    ) {
        // SAT_033: this fires on a fixed timer (SatchelBundle.pulseSync), deliberately not
        // gated on bundle.isDirty() -- project owner's call. Parcels have no delivery
        // acknowledgment and there's no client-side "resend please" request path
        // (SatchelNetwork is fire-and-forget), so this periodic resend is the only thing that
        // heals a dropped parcel or catches a client up if it connected mid-session before the
        // next real state change. That safety net stays. What doesn't need to stay is doing the
        // work when there's nobody to receive it -- the project owner's original log was an
        // empty world, still re-serializing and re-sending the same bundle every 5 seconds
        // forever with zero players connected.
        if (noOneWouldReceiveThis(info)) {
            return;
        }

        OUT.TRACE().log("[server engine] Sending Parcel for bundle: " + key.id);

        ParcelEgressSink
                .forBundle(info, key)
                .emit(bundle.saveAll());
    }

    /**
     * True if this scope's sync target currently has nobody able to receive it, so the
     * NBT-serialize-and-send work in {@link #scheduleSync} can be skipped this pulse.
     * {@link com.arryn.satchel.common.net.SatchelNetwork#send} only supports {@link LevelScope}
     * today (its own doc: "future scope types need different distribution logic entirely," see
     * SAT_028) -- for a level-scoped bundle, its audience is whoever's in that dimension, so an
     * empty dimension really does mean nobody would receive this. Any other scope kind (a future
     * {@code PlayerScope}-backed networked bundle, for instance) doesn't have a dimension-wide
     * audience to check this way -- conservatively assume someone might receive it rather than
     * guessing at a different kind of scope's audience.
     */
    private boolean noOneWouldReceiveThis(ScopeInfo info) {
        if (!(info.scope() instanceof LevelScope levelScope)) {
            return false;
        }
        return levelScope.level() instanceof ServerLevel serverLevel
                && serverLevel.players().isEmpty();
    }

    public String debugName() {
        return getClass().getSimpleName()
                + "@" + System.identityHashCode(this);
    }
    public enum CapableOf {
        PERSISTENCE,
        NETWORKING,
        CLOCK
    }


    private ServerLevel resolveServerLevel(
            ScopeInfo info,
            CapableOf requirement
    ) {
        Objects.requireNonNull(info, "info");
        requireConfig();

        // SAT_053 (revised 2026-09-10, project owner direction): whether this capability is
        // NEEDED at all is the caller's question, answered by bundle.required() (a per-bundle-
        // author declaration) before this method is ever invoked -- see hydrateBundle() /
        // flushIfDirty(). What THIS method checks is a different, jig-level question: does this
        // jig even declare the capability allowed/supported at all? Jig offers its own capable
        // policies; bundle handles its own requirements -- a bundle asking for something its own
        // jig doesn't allow is a real wiring mistake between the bundle author and the jig
        // author, and it crashes here, loudly, at the moment it's discovered, rather than
        // silently skipping (the old null-return contract this replaced) or silently working
        // around it.
        JigPolicies.Capabilities caps = info.policies().capabilities();

        boolean jigAllows = switch (requirement) {
            case PERSISTENCE -> caps.allowsPersistence();
            case NETWORKING -> caps.allowsNetworking();
            case CLOCK -> caps.allowsClock();
        };

        if (!jigAllows) {
            throw new SatchelException.AccessFailed(
                    "Bundle requires capability " + requirement + ", but its jig (" +
                            info.debugName() + ") does not declare that capability allowed -- " +
                            "add it via JigPolicies.Capabilities, or the bundle shouldn't be " +
                            "declaring this requirement."
            );
        }

        Level level = info.referenceLevel();

        if (level == null) {
            throw new SatchelException.AccessFailed(
                    "Capability " + requirement +
                            " requires Level, but no referenceLevel available: " +
                            info.debugName()
            );
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            throw new SatchelException.AccessFailed(
                    "Capability " + requirement +
                            " requires ServerLevel, but got: " +
                            level.getClass().getName()
            );
        }

        return serverLevel;
    }
}