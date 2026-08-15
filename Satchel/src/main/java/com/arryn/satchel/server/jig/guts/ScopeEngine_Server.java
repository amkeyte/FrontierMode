package com.arryn.satchel.server.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.LifecycleState;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.*;
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
        if (info == null) return;

        Map<BundleKey<?>, SatchelBundle> map = bundlesFor(info);
        if (map == null || map.isEmpty()) return;

        for (BundleKey<?> key : List.copyOf(map.keySet())) {
            SatchelBundle bundle = map.get(key);
            if (bundle == null) continue;

            bundle.pulseSync(info);
            bundle.healthCheckPulse();

            if (bundle.isDirty()) {
                flushIfDirty(info, key, bundle);
            }
        }
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

        // Terminal maintenance flush
        onExecutionPulse(info);

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
        boolean hydratedBefore = bundle.isHydrated();

        // resolveServerLevel only returns null when persistence isn't required for this
        // jig -- the required-but-broken case always throws instead of returning null
        // (SAT_013). A null here means "nothing to hydrate," not an error.
        ServerLevel level =
                resolveServerLevel(info, CapableOf.PERSISTENCE);

        if (level != null) {
            Optional<SavedDataHydrationSource> source =
                    SavedDataHydrationSource.forBundle(level, key);

            // bundle.hydrateFrom(...), not a raw FixtureHydrator call -- FixtureHydrator
            // explicitly documents itself as performing no lifecycle transitions, so calling it
            // directly (the previous code here) meant no server bundle, ever, with or without
            // real saved data, transitioned past CREATED. hydrateFrom() does the CREATED ->
            // HYDRATED transition and sets isHydrated(), which is what lets the check below
            // actually fire bundle.onLoaded(). When no saved data exists yet (first-ever
            // creation -- the common case for a fresh world), still hydrate from an explicitly
            // empty source rather than skipping entirely: the bundle still needs to reach
            // ACTIVE, since saveAll()/onJigTick() both require LOADED/ACTIVE, and "nothing to
            // load" is a normal state, not a reason to leave the bundle stuck. See SAT_027.
            bundle.hydrateFrom(
                    source.isPresent()
                            ? source.get()
                            : new NbtFixtureHydrationSource(new CompoundTag())
            );
        }

        if (!hydratedBefore && bundle.isHydrated()) {
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

    private void flushIfDirty(
            ScopeInfo info,
            BundleKey<?> key,
            SatchelBundle bundle
    ) {
        if (!bundle.isDirty()) return;

        // Same null-means-skip contract as hydrateBundle() (SAT_013) -- nothing to
        // flush to if persistence isn't required for this jig.
        ServerLevel level =
                resolveServerLevel(info, CapableOf.PERSISTENCE);

        if (level == null) {
            return;
        }

        SavedDataEgressSink
                .forBundle(level, key)
                .store(bundle.saveAll());

        bundle.clearDirty();
    }

    private void scheduleSync(
            ScopeInfo info,
            BundleKey<?> key,
            SatchelBundle bundle
    ) {
        OUT.TRACE().log("[server engine] Sending Parcel for bundle: " + key.id);

        ParcelEgressSink
                .forBundle(info, key)
                .emit(bundle.saveAll());
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

        // Deliberately info.policies(), not this.policies(): this engine is a single per-side
        // singleton shared by every jig (LogicalFoundation.installConfigs() calls
        // engine.installJigConfig(config) once per jig, on the same shared instance, via
        // JigConfigCompiler.instantiateCoupler -- each call plainly overwrites the engine's own
        // binding/execution/policies/bundles fields, so this.policies() reflects whichever jig
        // was installed *last* in that loop, not the jig actually asking here. info (the
        // ScopeInfo for the specific scope being hydrated/flushed) carries its own correctly
        // per-jig-scoped policies, installed once by JigInfo.addScope() and never touched again
        // -- that's the one that must be consulted. See SAT_023.
        JigPolicies.Capabilities caps = info.policies().capabilities();

        boolean required = switch (requirement) {
            case PERSISTENCE -> caps.requiresPersistence();
            case NETWORKING -> caps.requiresNetworking();
            case CLOCK -> caps.requiresClock();
        };

        if (!required) {
            return null; // capability not required → skip
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