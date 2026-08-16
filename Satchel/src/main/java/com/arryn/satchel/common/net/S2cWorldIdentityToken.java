package com.arryn.satchel.common.net;

import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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
public final class S2cWorldIdentityToken {

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

    public static void handle(
            S2cWorldIdentityToken packet,
            Supplier<NetworkEvent.Context> ctx
    ) {
        try {
            WorldIdentityContext.bindClientToken(packet.token());
        } catch (Throwable t) {
            OUT.TRACE().log("[Satchel] Failed to bind received world-identity token");
        } finally {
            ctx.get().setPacketHandled(true);
        }
    }
}
