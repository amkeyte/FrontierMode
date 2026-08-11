package com.arryn.satchel.common.jig.level;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.ASatchelJig;
import com.arryn.satchel.common.jig.guts.ScopeCoupler;

import java.util.Objects;
import java.util.UUID;

public final class LevelJig extends ASatchelJig<LevelScope> {

    public Class<LevelScope> scopeType(){return LevelScope.class;}

    public LevelJig() {
    }

    //-----------------------------------------------------------------
    // Scope identity & resolution
    // ---------------------------------------------------------------------


    @Deprecated
    @Override
    public Class<? extends ScopeCoupler> couplerClass() {
        return LevelScopeCoupler.class;
    }


    @Override
    public  LevelScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");
        return LevelResolver.resolveScope( source);
    }

    @SuppressWarnings("unchecked")
    @Override
    public JigKey<LevelJig> jigKey() {
        return (JigKey<LevelJig>) super.jigKey();
    }

    /**
     * Deterministic UUID for a level scopeInfo, derived solely from dimension ID.
     */
    @Override
    public UUID determineUUID(Object source) {
        return LevelResolver.determineUUID(source);
    }
}
