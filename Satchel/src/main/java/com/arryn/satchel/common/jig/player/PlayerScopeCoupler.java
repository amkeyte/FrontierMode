package com.arryn.satchel.common.jig.player;

import com.arryn.satchel.common.jig.guts.AScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * Policy boundary for player-scoped bundles. RM_SAT_020 rebuild, mirrors
 * {@link com.arryn.satchel.common.jig.level.LevelScopeCoupler}'s shape exactly: no extra
 * readiness gating beyond the scope existing. Unlike {@code LevelScope}, a {@link PlayerScope} is
 * only ever constructed for an already-logged-in {@code ServerPlayer} (see
 * {@link PlayerResolver#resolveScope}), so there's no analogous "world-identity token not arrived
 * yet" deferral window to gate on here.
 */
public final class PlayerScopeCoupler extends AScopeCoupler {

    public PlayerScopeCoupler() {
    }

    @Override
    public boolean markReady(ScopeInfo info) {
        return true;
    }
}
