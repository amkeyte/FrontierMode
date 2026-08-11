package com.arryn.satchel.common.jig.level;

import com.arryn.satchel.common.jig.guts.AScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * This class defines the policy boundary for level-scoped bundles.
 */
public final class LevelScopeCoupler extends AScopeCoupler {

    public LevelScopeCoupler() {
    }


    @Override
    public boolean markReady(ScopeInfo info) {
        return true;
    }

}
