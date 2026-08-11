package com.arryn.satchel.common.jig.model;

import com.arryn.satchel.common.jig.guts.AScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

public class ModelCoupler extends AScopeCoupler {
    @Override
    public boolean markReady(ScopeInfo info) {
        return true;
    }
}
