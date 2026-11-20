package com.arryn.satchel.common.jig.player;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.AScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * Policy boundary for player-scoped bundles. RM_SAT_020 rebuild, mirrors
 * {@link com.arryn.satchel.common.jig.level.LevelScopeCoupler}'s shape exactly: no extra
 * readiness gating beyond the scope existing. Unlike {@code LevelScope}, a {@link PlayerScope} is
 * only ever constructed for an already-logged-in player, either side (see
 * {@link PlayerResolver#resolveScope}), so there's no analogous "world-identity token not arrived
 * yet" deferral window to gate on here. Not final: a module may extend it and override a hook to
 * add a gate, and inherits a pass-through for every hook it leaves alone.
 */
public class PlayerScopeCoupler extends AScopeCoupler {

    public PlayerScopeCoupler() {
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
