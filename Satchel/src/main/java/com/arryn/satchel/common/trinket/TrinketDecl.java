package com.arryn.satchel.common.trinket;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A jig config's declaration of one Trinket: its type, which is also its lookup key, and the
 * factory Satchel calls once per scope when the scope loads.
 */
public record TrinketDecl<T extends Trinket>(Class<T> type, Supplier<? extends T> factory) {

    public TrinketDecl {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
    }
}
