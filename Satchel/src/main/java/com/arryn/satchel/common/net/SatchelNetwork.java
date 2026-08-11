package com.arryn.satchel.common.net;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

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


    }
// Currently routes scope packets to all clients in the scope's dimension.
// Future scope types may require different PacketDistributors.

    public static void send(
            ScopeInfo info,
            Object packet
    ) {
        Satchel.requireServer();

        ServerLevel level = info.scopeAs();

        CHANNEL.send(
                PacketDistributor.DIMENSION.with(
                        level::dimension
                ),
                packet
        );
    }


    private SatchelNetwork() {
    }
}
