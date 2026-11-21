package com.arryn.satchel.common.identity;

import com.arryn.satchel.common.bundle.SatchelBundle;

import java.util.UUID;

public final class BundleKey<T extends SatchelBundle>
        extends SatchelKey<T> {

    public BundleKey(String name, Class<T> type) {
        super(name, type);
    }

    public BundleKey(UUID id, String name, Class<T> type) {
        super(id, name, type);
    }
}
