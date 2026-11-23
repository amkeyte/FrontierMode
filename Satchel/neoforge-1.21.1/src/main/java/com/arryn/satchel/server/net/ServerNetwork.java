package com.arryn.satchel.server.net;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.net.S2cWorldIdentityToken;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * The server's half of Satchel's networking: sends payloads through NeoForge's {@link PacketDistributor}
 * to the clients that should have them. It is here, not in {@code SatchelNetwork}, because it names
 * {@link ServerLevel} and {@link ServerPlayer}; the payload registration, which both sides run, stays in
 * {@code SatchelNetwork}. A packet shared code hands over is one of Satchel's payloads.
 */
@PlatformBound(reason = "network")
public final class ServerNetwork {

// Currently routes scope packets to all clients in the scope's dimension.
// Future scope types may require different PacketDistributors.

    public static void send(
            ScopeInfoImpl info,
            Object packet
    ) {
        Satchel.requireServer();

        // A jig that doesn't allow networking (JigPolicies.Capabilities.allowsNetworking()) never
        // sends -- silent no-op, not an error. See SAT_062.
        if (!info.policies().capabilities().allowsNetworking()) {
            return;
        }

        // info.scopeAs() is an unchecked cast to whatever the call site infers -- the actual
        // runtime scope object is a LevelScope (wrapping a Level), never a ServerLevel itself.
        // ServerLevel level = info.scopeAs() compiled fine (erasure) and threw ClassCastException
        // the first time this method actually ran, since flushIfDirty()/pulseSync() never
        // reached scheduleSync() before SAT_027/FRO_018 fixed the chain ahead of it. Go through
        // LevelScope.level() (the scope's own real accessor) instead, then cast to ServerLevel --
        // safe here since Satchel.requireServer() above already guarantees server side. Matches
        // this method's own doc: currently LevelScope-only, future scope types need different
        // distribution logic entirely. See SAT_028.
        LevelScope levelScope = info.scopeAs();
        ServerLevel level = (ServerLevel) levelScope.level();

        PacketDistributor.sendToPlayersInDimension(level, (CustomPacketPayload) packet);
    }


    /**
     * {@link PlayerScope} counterpart to {@link ServerNetwork#send(ScopeInfoImpl, Object)} above -- that
     * method is {@link LevelScope}-only by its own doc comment; this is the "future scope type needs
     * different distribution logic entirely" case it already anticipated. Modeled directly on
     * {@link ServerNetwork#sendToken(ServerPlayer, UUID)}'s own per-player send
     * (SAT_063) -- per-player, not per-dimension, since a {@link PlayerScope}'s audience is
     * exactly one connection, not everyone in some dimension.
     */
    public static void sendToPlayer(
            ScopeInfoImpl info,
            Object packet
    ) {
        Satchel.requireServer();

        // Same silent-no-op-on-disallowed shape as send() above -- see SAT_062.
        if (!info.policies().capabilities().allowsNetworking()) {
            return;
        }

        // info.scopeAs() erases to whatever the call site infers -- the real runtime scope is a
        // PlayerScope, and PlayerScope.player() returns the abstract Player (SAT_063 widened it
        // for real client-side PlayerJig existence). Safe to cast to ServerPlayer here
        // specifically: Satchel.requireServer() above already guarantees this method only ever
        // runs server-side, where the backing Player is always genuinely a ServerPlayer.
        PlayerScope playerScope = info.scopeAs();
        ServerPlayer player = (ServerPlayer) playerScope.player();

        PacketDistributor.sendToPlayer(player, (CustomPacketPayload) packet);
    }

    /**
     * RM_SAT_019 — push the world-identity token to one player.
     *
     * <p>
     * Per-player, not per-scope/dimension like {@link ServerNetwork#send(ScopeInfoImpl, Object)} above —
     * the token is world-level state, so it goes out with {@link PacketDistributor#sendToPlayer} to the
     * player's own connection rather than any dimension's audience. Called from
     * {@code ServerLifecycle} at login and again on each dimension change (see class docs on
     * {@link S2cWorldIdentityToken}).
     */
    public static void sendToken(ServerPlayer player, UUID token) {
        Satchel.requireServer();

        PacketDistributor.sendToPlayer(player, new S2cWorldIdentityToken(token));
    }

    /**
     * A level scope's bundle to one player who has just joined its audience (SAT_144): at login, on a
     * dimension change, on a respawn. {@link ServerNetwork#send(ScopeInfoImpl, Object)} reaches everyone in
     * the dimension; this reaches only {@code player}, so a second player joining does not resend to the
     * first. The same silent no-op as the others for a jig that does not allow networking.
     */
    public static void sendToPlayer(
            ServerPlayer player,
            ScopeInfoImpl info,
            Object packet
    ) {
        Satchel.requireServer();

        if (!info.policies().capabilities().allowsNetworking()) {
            return;
        }

        PacketDistributor.sendToPlayer(player, (CustomPacketPayload) packet);
    }

    private ServerNetwork() {
    }
}
