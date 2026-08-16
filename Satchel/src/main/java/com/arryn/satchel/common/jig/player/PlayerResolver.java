package com.arryn.satchel.common.jig.player;

import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * RM_SAT_020 rebuild. Not a resurrection of the old {@code PlayerResolver} -- that version
 * (package {@code com.arryn.satchel.common.requireJig.player}) predates both the jig/requireJig
 * rename and the current {@code SatchelScope}/{@code ScopeCoupler} shape; kept only as a sketch
 * of intent (see that node's log). Mirrors {@link com.arryn.satchel.common.jig.level.LevelResolver}'s
 * shape.
 */
public final class PlayerResolver {

    private PlayerResolver() {}

    /**
     * Resolves a {@link PlayerScope} from a raw Forge source object. Unlike
     * {@code LevelResolver.resolveScope}, there is no readiness-gated defer window here:
     * {@link com.arryn.satchel.common.newconfig.newnew.PlayerJigConfig} defaults
     * {@code sideApplicability} to {@code SERVER}, and {@code Satchel.isReady()} is trivially true
     * on the server once a foundation is installed (see {@code LogicalFoundation.isReady()}) --
     * there is no server-side equivalent of the client's "world-identity token hasn't arrived yet"
     * window this jig kind would need to defer through.
     */
    public static PlayerScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");

        if (!(source instanceof ServerPlayer player)) {
            return null;
        }

        return new PlayerScope(player);
    }

    public static Class<ServerPlayer> sourceType() {
        return ServerPlayer.class;
    }

    /**
     * Deterministic UUID for a player scope, derived from the player's own persistent UUID --
     * stable across reconnects. Deliberately does <b>not</b> fold in the world-identity token the
     * way {@code LevelResolver.determineUUID} does: a player's identity isn't level-scoped, and
     * {@link PlayerScope} is only ever (re)resolved on login/logout
     * ({@code ServerForgeIngress}'s {@code PlayerLoggedInEvent}/{@code PlayerLoggedOutEvent}
     * handlers) -- never on dimension change -- so the scope has to agree with itself only across
     * one continuous login session, and the player's own UUID already does that without help.
     *
     * @throws IllegalStateException if {@code source} isn't a {@link ServerPlayer} -- mirrors
     *         {@code LevelResolver.determineUUID}'s own contract (a real programming error, not a
     *         timing window, so it throws rather than returning null).
     */
    public static UUID determineUUID(Object source) {
        if (!(source instanceof ServerPlayer player)) {
            throw new IllegalStateException(
                    "PlayerJig source must be a ServerPlayer, got: "
                            + source.getClass().getName()
            );
        }

        return UUID.nameUUIDFromBytes(
                ("player:" + player.getUUID()).getBytes(StandardCharsets.UTF_8)
        );
    }
}
