package com.arryn.satchel.common.net;

import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.util.Ids;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Satchel 2.0 — Unified networking entry point.
 *
 * This class owns the Satchel SimpleChannel and registers packet codecs for:
 *
 *   • S2C bundle sync (unified bundle parcel format)
 *   • S2C player bundle sync (legacy, until player 2.0 migration)
 *   • C2S request parcel routing
 *
 * <p>Sending is server code: {@code ServerNetwork}, in the server packages, sends down {@link
 * SatchelNetwork#CHANNEL}.
 */
@PlatformBound(reason = "network")
public final class SatchelNetwork {

    // Bump when the wire format changes (e.g., S2cBundleParcel adoption)
    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.ChannelBuilder
                    .named(Ids.id("satchel", "network"))
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

    private SatchelNetwork() {
    }
}
