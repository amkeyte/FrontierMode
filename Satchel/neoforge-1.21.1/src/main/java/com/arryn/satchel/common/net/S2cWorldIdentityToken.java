package com.arryn.satchel.common.net;

import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * RM_SAT_019 — S2C packet carrying the server's world-identity token.
 *
 * <p>
 * Sent at join, and again on each dimension change per the node's "cheap on each dimension
 * change, for robustness against a missed initial packet" policy — always the same UUID for one
 * server session, so re-delivery is harmless, just a {@link WorldIdentityContext#bindClientToken}
 * re-bind to the same value.
 *
 * <p>
 * Deliberately a single-field packet rather than folded into {@code S2cBundleParcel} — this is
 * world-level state, not scope/bundle-level state, and has its own delivery policy
 * (join + dimension-change) distinct from bundle sync's.
 */
@PlatformBound(reason = "network")
public final class S2cWorldIdentityToken implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2cWorldIdentityToken> TYPE =
            new CustomPacketPayload.Type<>(Ids.id(SatchelMod.MODID, "world_identity_token"));

    public static final StreamCodec<FriendlyByteBuf, S2cWorldIdentityToken> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> encode(pkt, buf), S2cWorldIdentityToken::decode);

    private final UUID token;

    public S2cWorldIdentityToken(UUID token) {
        this.token = token;
    }

    public UUID token() {
        return token;
    }

    public static void encode(S2cWorldIdentityToken pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.token);
    }

    public static S2cWorldIdentityToken decode(FriendlyByteBuf buf) {
        return new S2cWorldIdentityToken(buf.readUUID());
    }

    @Override
    public Type<S2cWorldIdentityToken> type() {
        return TYPE;
    }

    /** Runs on the client's main thread, NeoForge's default for a payload handler. */
    public static void handle(S2cWorldIdentityToken packet, IPayloadContext ctx) {
        try {
            WorldIdentityContext.bindClientToken(packet.token());
        } catch (Throwable t) {
            OUT.TRACE().log("[Satchel] Failed to bind received world-identity token");
        }
    }
}
