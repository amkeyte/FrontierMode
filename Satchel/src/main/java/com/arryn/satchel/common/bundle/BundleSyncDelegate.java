package com.arryn.satchel.common.bundle;

import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;

@FunctionalInterface
public interface BundleSyncDelegate {
    void sync(
        ScopeInfoImpl info,
        BundleKey<?> key,
        SatchelBundle bundle);
}
