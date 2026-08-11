package com.arryn.satchel.client.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.bundle.builder.BundleFactories;
import com.arryn.satchel.common.bundle.builder.BundleFactoryEntry;
import com.arryn.satchel.common.bundle.builder.FixtureRegistration;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.net.ParcelInbox;
import com.arryn.satchel.common.net.S2cBundleParcel;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.util.out.OUT;

import java.util.*;

public final class ScopeEngine_Client implements ScopeEngine {

    public ScopeEngine_Client() {
        Satchel.requireClient();
    }

    private final Map<UUID, Map<BundleKey<?>, SatchelBundle>> active = new HashMap<>();

    private Map<BundleKey<?>, SatchelBundle> bundlesFor(ScopeInfo info) {
        return active.computeIfAbsent(info.scopeId(), id -> new HashMap<>());
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
            throw new IllegalStateException(
                    "No bundles registered for scope: " + info.debugName()
            );
        }

        SatchelBundle b = map.get(key);
        if (b == null) {
            throw new IllegalStateException(
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
                bundle.hydrateAll(parcel.data());
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
            if (bundle == null) continue;

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
