package com.arryn.satchel.common.net;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * Satchel 2.0 — Unified networking entry point.
 *
 * This class owns the Satchel SimpleChannel and registers packet codecs for:
 *
 *   • S2C bundle sync (unified bundle parcel format)
 *   • S2C player bundle sync (legacy, until player 2.0 migration)
 *   • C2S request parcel routing
 */
public final class SatchelNetwork {

    // Bump when the wire format changes (e.g., S2cBundleParcel adoption)
    private static final String PROTOCOL = "2";

    @SuppressWarnings("ResourceLocation(java.lang.String, java.lang.String)' is deprecated since version 1.20.6 and marked for removal")
    public static final SimpleChannel CHANNEL =
            NetworkRegistry.ChannelBuilder
                    .named(new ResourceLocation("satchel", "network"))
                    .networkProtocolVersion(() -> PROTOCOL)
                    .clientAcceptedVersions(PROTOCOL::equals)
                    .serverAcceptedVersions(PROTOCOL::equals)
                    .simpleChannel();

    private static int nextId = 0;
    private static int id() { return nextId++; }

    /**
     * Register all Satchel packets.
     *
     * Satchel 2.0 removes any auto-registration or discovery-based networking.
     * All packets must be explicitly listed here in a deterministic order.
     */
    public static void register() {

        // ------------------------------------------------------------
        //  S2C — Unified bundle sync (level / other scopes)
        // ------------------------------------------------------------
        CHANNEL.messageBuilder(S2cBundleParcel.class, id())
                .encoder(S2cBundleParcel::encode)
                .decoder(S2cBundleParcel::decode)
                .consumerMainThread(S2cBundleParcel::handle)
                .add();

        // ------------------------------------------------------------
        //  S2C — World-identity token (RM_SAT_019)
        // ------------------------------------------------------------
        CHANNEL.messageBuilder(S2cWorldIdentityToken.class, id())
                .encoder(S2cWorldIdentityToken::encode)
                .decoder(S2cWorldIdentityToken::decode)
                .consumerMainThread(S2cWorldIdentityToken::handle)
                .add();

    }
// Currently routes scope packets to all clients in the scope's dimension.
// Future scope types may require different PacketDistributors.

    public static void send(
            ScopeInfo info,
            Object packet
    ) {
        Satchel.requireServer();

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

        CHANNEL.send(
                PacketDistributor.DIMENSION.with(
                        level::dimension
                ),
                packet
        );
    }


    /**
     * RM_SAT_019 — push the world-identity token to one player.
     *
     * <p>
     * Per-player, not per-scope/dimension like {@link #send(ScopeInfo, Object)} above — the token
     * is world-level state, so it goes out via {@link PacketDistributor#PLAYER} keyed on the
     * player's own connection rather than any dimension's audience. Called from
     * {@code ServerForgeIngress} at login and again on each dimension change (see class docs on
     * {@link S2cWorldIdentityToken}).
     */
    public static void sendToken(ServerPlayer player, UUID token) {
        Satchel.requireServer();

        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2cWorldIdentityToken(token)
        );
    }

    private SatchelNetwork() {
    }
}
