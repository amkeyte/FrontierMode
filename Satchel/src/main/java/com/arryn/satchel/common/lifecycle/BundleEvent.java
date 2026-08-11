package com.arryn.satchel.common.lifecycle;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

import java.util.Objects;

public abstract sealed class BundleEvent
        permits BundleEvent.Loaded,
        BundleEvent.Tick,
        BundleEvent.Unloaded {

    private final ScopeInfo scope;
    private final SatchelBundle bundle;

    protected BundleEvent(ScopeInfo scopeInfo, SatchelBundle bundle) {
        this.scope = Objects.requireNonNull(scopeInfo, "scopeInfo");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    public ScopeInfo scopeInfo() {
        return scope;
    }

    public SatchelBundle bundle() {
        return bundle;
    }

    /* =============================================================
     * Event types
     * ========================================================== */

    public static final class Loaded extends BundleEvent {
        public Loaded(ScopeInfo scopeInfo, SatchelBundle bundle) {
            super(scopeInfo, bundle);
        }
    }

    public static final class Tick extends BundleEvent {
        public Tick(ScopeInfo scopeInfo, SatchelBundle bundle) {
            super(scopeInfo, bundle);
        }
    }

    public static final class Unloaded extends BundleEvent {
        public Unloaded(ScopeInfo scopeInfo, SatchelBundle bundle) {
            super(scopeInfo, bundle);
        }
    }
}
