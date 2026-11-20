package com.arryn.satchel.common.jig.level;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.AScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * This class defines the policy boundary for level-scoped bundles. It gates nothing: each hook is
 * written out and passes through. Not final: a module may extend it and override a hook to add a
 * gate, and inherits a pass-through for every hook it leaves alone.
 */
public class LevelScopeCoupler extends AScopeCoupler {

    public LevelScopeCoupler() {
    }


    @Override
    public boolean markReady(ScopeInfo info) {
        return true;
    }

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
