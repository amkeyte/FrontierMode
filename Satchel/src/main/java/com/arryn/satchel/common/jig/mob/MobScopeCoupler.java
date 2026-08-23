package com.arryn.satchel.common.jig.mob;

import com.arryn.satchel.common.jig.guts.AScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * Policy boundary for mob-scoped bundles. Mirrors
 * {@link com.arryn.satchel.common.jig.player.PlayerScopeCoupler}'s shape exactly: no extra
 * readiness gating beyond the scope existing -- a {@link MobScope} is only ever constructed for a
 * currently-resolved, non-removed mob (via the poll's reconciliation or {@link MobScope#getFor}),
 * so there's no analogous deferral window to gate on here.
 */
public final class MobScopeCoupler extends AScopeCoupler {

    public MobScopeCoupler() {
    }

    @Override
    public boolean markReady(ScopeInfo info) {
        return true;
    }
}
