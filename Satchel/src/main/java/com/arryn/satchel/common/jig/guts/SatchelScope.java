package com.arryn.satchel.common.jig.guts;


import java.util.UUID;

/**
 * A logical identity that owns zero or more Satchel DataBundles.
 * <p>
 * Scopes DO NOT know about persistence, level access, or networking.
 * All policy and storage is delegated to their ScopeCoupler.
 * a scopeInfo is treated as a value scopeType that has a deterministic id
 * calculated at construction. any SatchelScope constructed for
 * a given source is considered equal.
 */
public interface SatchelScope {

    /**
     * Stable unique ID for identifying this scopeInfo.
     */
    UUID uuid();

    /**
     * For debug/logging only.
     */
    String debugName();
}
