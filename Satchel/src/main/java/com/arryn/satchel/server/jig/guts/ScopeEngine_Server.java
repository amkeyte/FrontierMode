package com.arryn.satchel.server.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.LifecycleState;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.bundle.builder.BundleFactories;
import com.arryn.satchel.common.bundle.builder.BundleFactoryEntry;
import com.arryn.satchel.common.bundle.builder.FixtureRegistration;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.*;
import com.arryn.satchel.common.newstuff.FixtureHydrator;
import com.arryn.satchel.common.newstuff.ParcelEgressSink;
import com.arryn.satchel.common.newstuff.SavedDataEgressSink;
import com.arryn.satchel.common.newstuff.SavedDataHydrationSource;
import com.arryn.satchel.common.util.out.OUT;
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

        BundleFactoryEntry<B> entry = BundleFactories.entryFor(key);
        if (entry == null) {
            throw new IllegalStateException("No BundleFactory registered for " + key);
        }

        SatchelScope scope = Objects.requireNonNull(info.scope(), "scope");

        B bundle = Objects.requireNonNull(
                entry.bundleFactory.apply(scope),
                "Bundle factory returned null for " + key
        );

        OUT.TRACE().log("[engine] SERVER Bundle Created: " + bundle.debugName());
        OUT.TRACE().log("         using " + key);
        OUT.TRACE().log("         in scope " + info.debugName());

        // Define bundle shape immediately
        for (FixtureRegistration<?> reg : entry.fixtureRegistrations) {
            applyFixture(bundle, reg);
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

        Map<BundleKey<?>, SatchelBundle> map = bundlesFor(info);
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

        ServerLevel level =
                resolveServerLevel(info, CapableOf.PERSISTENCE);

        if (level == null) {
            throw new SatchelException.AccessFailed(
                    "Source is not a ServerLevel. Type: "
                            + info.source().getClass().getName()
            );
        }

        SavedDataHydrationSource
                .forBundle(level, key)
                .ifPresent(source ->
                        new FixtureHydrator(bundle, source)
                                .hydrateExisting()
                );

        if (!hydratedBefore && bundle.isHydrated()) {
            bundle.onLoaded();
            Satchel.require().bundleLifecycle()
                    .signalBundleLoaded(info, bundle);
        }
    }

    private <F extends SatchelFixture> void applyFixture(
            SatchelBundle bundle,
            FixtureRegistration<F> reg
    ) {
        bundle.getOrCreateFixture(reg.key(), reg.factory());
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

        ServerLevel level =
                resolveServerLevel(info, CapableOf.PERSISTENCE);

        if (level == null) {
            throw new SatchelException.AccessFailed(
                    "Source is not a ServerLevel. Type: "
                            + info.source().getClass().getName()
            );
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

        JigPolicies.Capabilities caps = policies().capabilities();

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