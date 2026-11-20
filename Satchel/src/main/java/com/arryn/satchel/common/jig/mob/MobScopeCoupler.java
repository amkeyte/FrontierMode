package com.arryn.satchel.common.jig.mob;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.AScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * Policy boundary for mob-scoped bundles. Mirrors
 * {@link com.arryn.satchel.common.jig.player.PlayerScopeCoupler}'s shape exactly: no extra
 * readiness gating beyond the scope existing -- a {@link MobScope} is only ever constructed for a
 * currently-resolved, non-removed mob (built by {@link MobJig}'s sweep or by a call to its {@code
 * interest} or {@code scopeFor}), so there's no analogous deferral window to gate on here. Not
 * final: a module may extend it and override a hook to add a gate, and inherits a pass-through for
 * every hook it leaves alone.
 */
public class MobScopeCoupler extends AScopeCoupler {

    public MobScopeCoupler() {
    }

    @Override
    public boolean markReady(ScopeInfo info) {
        return true;
    }

    // It gates nothing beyond that: each hook is written out and passes through.

    @Override
    public void beforeTick(ScopeInfo info) {
    }

    @Override
    public void afterTick(ScopeInfo info) {
    }

    @Override
    public void beforeUnload(ScopeInfo info) {
    }

    @Override
    public void afterUnload(ScopeInfo info) {
    }

    @Override
    public AccessDecision beforeAccess(ScopeInfo info, BundleKey<?> key) {
        return AccessDecision.ALLOW;
    }

    @Override
    public AccessDecision afterAccess(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle) {
        return AccessDecision.ALLOW;
    }

    @Override
    public AccessDecision beforeCreate(ScopeInfo info, BundleKey<?> key) {
        return AccessDecision.ALLOW;
    }

    @Override
    public AccessDecision afterCreate(ScopeInfo info, BundleKey<?> key, SatchelBundle bundle) {
        return AccessDecision.ALLOW;
    }
}
