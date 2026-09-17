package com.arryn.satchel.common.jig.player;

import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * RM_SAT_020 rebuild. Not a resurrection of the old {@code PlayerResolver} sketch -- that
 * version predates the current {@code SatchelScope}/{@code ScopeCoupler} shape and is kept
 * only as a sketch of intent (see that node's log), not a real prior implementation this one
 * continues. Mirrors {@link com.arryn.satchel.common.jig.level.LevelResolver}'s shape. Widened
 * from {@code ServerPlayer} to the abstract {@link Player} by SAT_063 -- see {@link
 * PlayerScope}'s own class doc for why.
 */
public final class PlayerResolver {

    private PlayerResolver() {}

    /**
     * Resolves a {@link PlayerScope} from a raw Forge source object -- either side, a {@code
     * ServerPlayer} (server) or a {@code LocalPlayer} (client), both real {@link Player}
     * instances. No readiness-gated defer window here, either side: unlike {@code
     * LevelResolver.resolveScope}, nothing about player-identity resolution depends on the
     * world-identity token (see {@link #determineUUID}'s own doc for why) or any other
     * readiness precondition -- a {@link Player} reference is either the real thing or it isn't.
     */
    public static PlayerScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");

        if (!(source instanceof Player player)) {
            return null;
        }

        return new PlayerScope(player);
    }

    public static Class<Player> sourceType() {
        return Player.class;
    }

    /**
     * Deterministic UUID for a player scope, derived from the player's own persistent UUID --
     * stable across reconnects, and identical on both sides for the same real player (a
     * player's UUID is the one piece of identity Mojang/Forge already guarantee agree between a
     * {@code ServerPlayer} and the corresponding client's {@code LocalPlayer}). Deliberately
     * does <b>not</b> fold in the world-identity token the way {@code LevelResolver.determineUUID}
     * does: a player's identity isn't level-scoped, and {@link PlayerScope} is only ever
     * (re)resolved on login/logout ({@code ServerForgeIngress}'s {@code
     * PlayerLoggedInEvent}/{@code PlayerLoggedOutEvent} handlers server-side, {@code
     * ClientForgeIngress}'s {@code ClientPlayerNetworkEvent.LoggingIn}/{@code LoggingOut}
     * client-side) -- never on dimension change -- so the scope has to agree with itself only
     * across one continuous login session, and the player's own UUID already does that without
     * help.
     *
     * @throws IllegalStateException if {@code source} isn't a {@link Player} -- mirrors
     *         {@code LevelResolver.determineUUID}'s own contract (a real programming error, not a
     *         timing window, so it throws rather than returning null).
     */
    public static UUID determineUUID(Object source) {
        if (!(source instanceof Player player)) {
            throw new IllegalStateException(
                    "PlayerJig source must be a Player, got: "
                            + source.getClass().getName()
            );
        }

        return UUID.nameUUIDFromBytes(
                ("player:" + player.getUUID()).getBytes(StandardCharsets.UTF_8)
        );
    }
}
