package com.arryn.satchel.common.jig.player;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.ASatchelJig;
import com.arryn.satchel.common.jig.guts.ScopeCoupler;

import java.util.Objects;
import java.util.UUID;

/**
 * RM_SAT_020 rebuild -- the third real jig kind, alongside {@code LevelJig} (the only other one
 * exercised by anything real; {@code ModelJig} was deleted, see the Jig & Scope Runtime wiki
 * page). Not a resurrection of the old, pre-rename {@code PlayerJig} -- see {@link PlayerResolver}
 * for why. Mirrors {@code LevelJig}'s shape exactly; see {@code PlayerJigConfig} for the
 * config-side defaults this jig is compiled against.
 */
public final class PlayerJig extends ASatchelJig<PlayerScope> {

    public Class<PlayerScope> scopeType() { return PlayerScope.class; }

    public PlayerJig() {
    }

    @Deprecated
    @Override
    public Class<? extends ScopeCoupler> couplerClass() {
        return PlayerScopeCoupler.class;
    }

    @Override
    public PlayerScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");
        return PlayerResolver.resolveScope(source);
    }

    @SuppressWarnings("unchecked")
    @Override
    public JigKey<PlayerJig> jigKey() {
        return (JigKey<PlayerJig>) super.jigKey();
    }

    /**
     * Deterministic UUID for a player scope, derived solely from the player's own persistent
     * UUID -- see {@link PlayerResolver#determineUUID} for the full reasoning.
     */
    @Override
    public UUID determineUUID(Object source) {
        return PlayerResolver.determineUUID(source);
    }
}
