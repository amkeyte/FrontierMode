package com.arryn.satchel.common.bundle.builder;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.util.out.OUT;

import java.util.Objects;
import java.util.function.Function;

/**
 * Registry of bundle construction functions.
 *
 * <p>Identity is handled by {@code SatchelKeys}. This registry is strictly about
 * how to construct a bundle instance for a given {@link BundleKey}.</p>
 *
 * <p>Factories must be registered explicitly. Missing factories are a hard error.</p>
 */
public final class BundleFactories {
    private BundleFactories() {
    }

    // ---------------------------------------------------------------------
    // Registration (fluent)
    // ---------------------------------------------------------------------

    public static <T extends SatchelBundle> BundleFactoryBuilder<T> registerFactory(BundleKey<T> key, Function<SatchelScope, T> factory) {
        Objects.requireNonNull(key, "bundle key");
        Objects.requireNonNull(factory, "bundle factory");

        BundleFactoryEntry<T> entry = new BundleFactoryEntry<>(key, factory);
        OUT.debug("Registering Bundle for key:" + key);
        BundleFactoryEntries.put(entry);
        return new BundleFactoryBuilder<>(entry);
    }

    // ---------------------------------------------------------------------
    // Internal access (used by BundlesAPI)
    // ---------------------------------------------------------------------

    public static <T extends SatchelBundle> BundleFactoryEntry<T> entryFor(BundleKey<T> key) {
        return BundleFactoryEntries.get(key);
    }
}
