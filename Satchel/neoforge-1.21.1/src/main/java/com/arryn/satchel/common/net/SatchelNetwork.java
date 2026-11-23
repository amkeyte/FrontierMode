package com.arryn.satchel.common.net;

import com.arryn.satchel.common.platform.PlatformBound;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Satchel's payload registration on NeoForge: the two packets the server sends a client, the bundle
 * parcel and the world-identity token, each a {@code CustomPacketPayload} with its own id and codec.
 *
 * <p>Sending is server code: {@code ServerNetwork}, in the server packages, sends through NeoForge's
 * {@code PacketDistributor}.
 */
@PlatformBound(reason = "network")
public final class SatchelNetwork {

    // Bump when the wire format changes.
    private static final String PROTOCOL = "2";

    /** Registers every Satchel payload. A listener on the mod bus, added by the mod constructor. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);

        // S2C: unified bundle sync (level and other scopes)
        registrar.playToClient(S2cBundleParcel.TYPE, S2cBundleParcel.STREAM_CODEC, S2cBundleParcel::handle);

        // S2C: world-identity token (RM_SAT_019)
        registrar.playToClient(S2cWorldIdentityToken.TYPE, S2cWorldIdentityToken.STREAM_CODEC,
                S2cWorldIdentityToken::handle);
    }

    private SatchelNetwork() {
    }
}
