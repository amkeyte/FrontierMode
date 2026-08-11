package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.common.jig.guts.SatchelScope;

import java.util.UUID;

public final class JigBinding {

    private JigBinding() {}

    @FunctionalInterface
    public interface ScopeResolver<S extends SatchelScope> {
        S resolve(Object source);
    }


    @FunctionalInterface
    public interface UuidDerterminer<SRC> {
        UUID derive(SRC source);
    }
}
