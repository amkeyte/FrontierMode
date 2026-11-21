package com.arryn.satchel.common.bundle.builder;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;

import java.util.HashMap;
import java.util.Map;

public final class BundleFactoryEntries {

    private static final Map<BundleKey<?>, BundleFactoryEntry<?>> ENTRIES =
            new HashMap<>();

    private BundleFactoryEntries() {}

    static <T extends SatchelBundle>
    BundleFactoryEntry<T> put(BundleFactoryEntry<T> entry) {
        if (ENTRIES.containsKey(entry.key)) {
            throw new IllegalStateException(
                    "Duplicate bundle factory for " + entry.key
            );
        }
        ENTRIES.put(entry.key, entry);
        return entry;
    }

    @SuppressWarnings("unchecked")
    static <T extends SatchelBundle>
    BundleFactoryEntry<T> get(BundleKey<T> key) {
        return (BundleFactoryEntry<T>) ENTRIES.get(key);
    }
}
