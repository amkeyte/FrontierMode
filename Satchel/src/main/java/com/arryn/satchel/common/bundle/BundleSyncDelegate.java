package com.arryn.satchel.common.bundle;

import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

@FunctionalInterface
public interface BundleSyncDelegate {
    void sync(
        ScopeInfo info,
        BundleKey<?> key,
        SatchelBundle bundle);
}
