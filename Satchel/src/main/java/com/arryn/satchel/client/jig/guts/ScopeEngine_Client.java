package com.arryn.satchel.client.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.LifecycleState;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.bundle.builder.BundleFactories;
import com.arryn.satchel.common.bundle.builder.BundleFactoryEntry;
import com.arryn.satchel.common.bundle.builder.FixtureRegistration;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.net.ParcelInbox;
import com.arryn.satchel.common.net.S2cBundleParcel;
import com.arryn.satchel.common.persistence.NbtFixtureHydrationSource;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.*;
import com.arryn.satchel.common.util.out.OUT;

import java.util.*;

public final class ScopeEngine_Client implements ScopeEngine {

    public ScopeEngine_Client() {
        Satchel.requireClient();
    }

    private final Map<UUID, Map<BundleKey<?>, SatchelBundle>> active = new HashMap<>();

    private final Map<BundleKey<?>, JigBundles.BundleDecl<?, ?>> bundleDecls =
            new LinkedHashMap<>();

    private boolean schemaFrozen = false;

    private Map<BundleKey<?>, SatchelBundle> bundlesFor(ScopeInfo info) {
        return active.computeIfAbsent(info.scopeId(), id -> new HashMap<>());
    }

    /* ------------------------------------------------------------
     * IJigConfigurable (mirrors ScopeEngine_Server)
     * --------------------------------------------------------- */

    private JigBindingConfig<?, ?> binding;
    private JigExecutionConfig execution;
    private JigPoliciesConfig policies;
    private JigBundlesConfig<?> configBundles;

    @Override
    public void installJigConfig(CompiledJigConfig config) {
        Objects.requireNonNull(config, "config");

        binding = config.binding();
        execution = config.execution();
        policies = config.policies();
        configBundles = config.bundles();
    }

    @Override
    public JigBindingConfig<?, ?> binding() { return binding; }

    @Override
    public JigExecutionConfig execution() { return execution; }

    @Override
    public JigPoliciesConfig policies() { return policies; }

    @Override
    public JigBundlesConfig<?> bundles() { return configBundles; }

    /* ------------------------------------------------------------
     * Bundle schema intake (mirrors ScopeEngine_Server)
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



    @Override
    @SuppressWarnings("unchecked")
    public <B extends SatchelBundle> Optional<B> ask(ScopeInfo info, BundleKey<B> key) {
        if (info == null || key == null) return Optional.empty();

        Map<BundleKey<?>, SatchelBundle> map = active.get(info.scopeId());
        if (map == null) return Optional.empty();

        return Optional.ofNullable((B) map.get(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends SatchelBundle> T get(ScopeInfo info, BundleKey<T> key) {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(key, "key");

        Map<BundleKey<?>, SatchelBundle> map = active.get(info.scopeId());
        if (map == null) {
            // SatchelException.BundleNotFound, not IllegalStateException -- mirrors
            // ScopeEngine_Server.get() exactly. AScopeCoupler.getOrCreate() only catches
            // BundleNotFound to fall through to create(); throwing the wrong type here meant the
            // client-side fallback never engaged at all -- every "no bundle yet" case (the normal
            // first-render state, same as server's own hydrate-on-first-load) crashed the render
            // thread instead of transparently creating the bundle. See SAT_024.
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

        return (T) b;
    }

    @Override
    public <T extends SatchelBundle> T create(ScopeInfo info, BundleKey<T> key) {
        Satchel.requireClient();
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(key, "key");

        Optional<T> existing = ask(info, key);
        if (existing.isPresent()) return existing.get();

        BundleFactoryEntry<T> entry = BundleFactories.entryFor(key);
        if (entry == null) {
            throw new IllegalStateException("No BundleFactory registered for " + key);
        }

        T bundle = Objects.requireNonNull(
                entry.bundleFactory.apply(info.scope()),
                "Bundle factory returned null for " + key
        );

        OUT.TRACE().log("[engine] CLIENT Bundle Created: " + bundle.debugName());
        OUT.TRACE().log("         using " + key);
        OUT.TRACE().log("         in scopeInfo " + info.debugName());

        for (FixtureRegistration<?> reg : entry.fixtureRegistrations) {
            applyFixture(bundle, reg);
        }

        // no-op on client
        bundle.attachSyncDelegate((scope, k, b) -> {});

        bundle.onCreated();
        bundlesFor(info).put(key, bundle);

        return bundle;
    }

    private <F extends SatchelFixture> void applyFixture(
            SatchelBundle bundle,
            FixtureRegistration<F> reg
    ) {
        bundle.getOrCreateFixture(reg.key(), reg.factory());
    }

    @Override
    public void onExecutionPulse(ScopeInfo info) {
        Satchel.requireClient();
        if (info == null) return;

        applyIncomingParcels(info);

        Map<BundleKey<?>, SatchelBundle> map = active.get(info.scopeId());
        if (map == null || map.isEmpty()) return;

        for (BundleKey<?> key : List.copyOf(map.keySet())) {
            SatchelBundle bundle = map.get(key);
            if (bundle == null) continue;

            bundle.pulseSync(info);

            if (bundle.isDirty()) {
                OUT.warn("[engine] CLIENT bundle became dirty (read-only violation): " + bundle.debugName());
                bundle.clearDirty();
            }
        }
    }

    private void applyIncomingParcels(ScopeInfo info) {
        Map<BundleKey<?>, SatchelBundle> map = active.get(info.scopeId());
        if (map == null || map.isEmpty()) {
            return; // IMPORTANT: don't drain yet
        }

        Collection<S2cBundleParcel> parcels =
                ParcelInbox.drainForScope(info.scopeId());
        if (parcels == null || parcels.isEmpty()) return;

        for (S2cBundleParcel parcel : parcels) {
            SatchelBundle bundle = findBundleById(map, parcel.bundleId());
            if (bundle == null) {
                OUT.TRACE().log("[engine] CLIENT dropped parcel for unknown bundleId=" + parcel.bundleId()
                        + " name=" + parcel.bundleName());
                continue;
            }

            boolean hydratedBefore = bundle.isHydrated();
            try {
                // hydrateAll() only fires once, ever -- its CREATED -> HYDRATED transition
                // throws on every subsequent call. Every parcel after the first for the same
                // bundle (i.e. every regular sync update once it's already LOADED/ACTIVE) needs
                // refreshFrom() instead, which applies the same data without touching lifecycle
                // state. Before this branch existed, every parcel past the first for a given
                // bundle logged "Operation not allowed in state ACTIVE (expected CREATED)" and
                // was silently dropped -- the client's fixture data was permanently stuck at
                // whatever the very first sync captured, regardless of how much real state
                // changed on the server afterward. See SAT_030.
                if (!hydratedBefore) {
                    bundle.hydrateAll(parcel.data());
                } else {
                    bundle.refreshFrom(new NbtFixtureHydrationSource(parcel.data()));
                }
            } catch (Throwable t) {
                OUT.error("[engine] CLIENT failed to apply parcel to " + bundle.debugName() + ": " + t);
                continue;
            }

            if (!hydratedBefore && bundle.isHydrated()) {
                bundle.onLoaded();
                Satchel.require().bundleLifecycle()
                        .signalBundleLoaded(info, bundle);
            }
        }
    }

    private static SatchelBundle findBundleById(Map<BundleKey<?>, SatchelBundle> map, UUID bundleId) {
        for (SatchelBundle b : map.values()) {
            if (b != null && b.key() != null && bundleId.equals(b.key().id)) {
                return b;
            }
        }
        return null;
    }

    @Override
    public void onJigTick(ScopeInfo info) {
        Satchel.requireClient();
        if (info == null) return;

        Map<BundleKey<?>, SatchelBundle> map = active.get(info.scopeId());
        if (map == null || map.isEmpty()) return;

        for (BundleKey<?> key : List.copyOf(map.keySet())) {
            SatchelBundle bundle = map.get(key);
            // Same guard ScopeEngine_Server.onJigTick() already has. A freshly `create()`d client
            // bundle sits in CREATED, not ACTIVE, until a server parcel arrives and
            // applyIncomingParcels() drives it through hydrateAll -> onLoaded -> ACTIVE -- that's
            // gated entirely on network sync timing, not on a jig tick. Without this check,
            // onJigTick() unconditionally called bundle.onJigTick(), which hard-requires ACTIVE
            // and throws otherwise -- crashed the render thread on the very next client tick
            // after any bundle was created via the getOrCreate() fallback, before its first
            // parcel had a chance to arrive. See SAT_025.
            if (bundle == null || bundle.lifeCycleState() != LifecycleState.ACTIVE) continue;

            bundle.onJigTick();
            Satchel.require().bundleLifecycle()
                    .signalBundleTick(info, bundle);
        }
    }

    @Override
    public void unload(ScopeInfo info) {
        Satchel.requireClient();
        if (info == null) return;

        Map<BundleKey<?>, SatchelBundle> map = active.remove(info.scopeId());
        if (map == null || map.isEmpty()) return;

        for (SatchelBundle bundle : map.values()) {
            Satchel.require().bundleLifecycle()
                    .signalBundleUnloaded(info, bundle);
            bundle.onDestroyed();
        }
    }

    public String debugName() {
        return getClass().getSimpleName() + "@" + System.identityHashCode(this);
    }
}
