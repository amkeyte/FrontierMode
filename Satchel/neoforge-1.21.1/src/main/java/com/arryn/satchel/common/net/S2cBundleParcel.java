package com.arryn.satchel.common.net;

import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@PlatformBound(reason = "network")
public final class S2cBundleParcel implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2cBundleParcel> TYPE =
            new CustomPacketPayload.Type<>(Ids.id(SatchelMod.MODID, "bundle_parcel"));

    public static final StreamCodec<FriendlyByteBuf, S2cBundleParcel> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> encode(pkt, buf), S2cBundleParcel::decode);

    private final UUID ownerScopeId;
    private final UUID bundleId;
    private final String bundleName;
    private final CompoundTag data;

    public S2cBundleParcel(
            UUID ownerScopeId,
            UUID bundleId,
            String bundleName,
            CompoundTag data
    ) {
        this.ownerScopeId = ownerScopeId;
        this.bundleId = bundleId;
        this.bundleName = bundleName;
        this.data = data;
    }

    public UUID scopeId() {
        return ownerScopeId;
    }

    public UUID bundleId() {
        return bundleId;
    }

    public String bundleName() {
        return bundleName;
    }

    public CompoundTag data() {
        return data;
    }
    public static void encode(S2cBundleParcel pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.ownerScopeId);
        buf.writeUUID(pkt.bundleId);
        buf.writeUtf(pkt.bundleName);
        buf.writeNbt(pkt.data);
    }

    public static S2cBundleParcel decode(FriendlyByteBuf buf) {
        return new S2cBundleParcel(
                buf.readUUID(),
                buf.readUUID(),
                buf.readUtf(),
                buf.readNbt()
        );
    }

    @Override
    public Type<S2cBundleParcel> type() {
        return TYPE;
    }

    /** Runs on the client's main thread, NeoForge's default for a payload handler. */
    public static void handle(S2cBundleParcel packet, IPayloadContext ctx) {
        try {
            ParcelInbox.enqueue(packet);
        } catch (Throwable t) {
            OUT.TRACE().log("[Satchel] Failed to enqueue S2cBundleParcel");
        }
    }


    public String dump() {
        StringBuilder sb = new StringBuilder();

        sb.append("S2cBundleParcel {\n");
        sb.append("  scopeId = ").append(ownerScopeId).append('\n');
        sb.append("  bundleId     = ").append(bundleId).append('\n');
        sb.append("  bundleName   = ").append(bundleName).append('\n');

        if (data == null) {
            sb.append("  data = <null>\n");
        } else if (data.isEmpty()) {
            sb.append("  data = <empty>\n");
        } else {
            sb.append("  data = ").append(prettyPrintNbt(data, "  ")).append('\n');
        }

        sb.append('}');
        return sb.toString();
    }
    private static String prettyPrintNbt(CompoundTag tag, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        String childIndent = indent + "  ";

        for (String key : tag.getAllKeys()) {
            sb.append(childIndent)
                    .append(key)
                    .append(": ");

            var value = tag.get(key);

            if (value instanceof CompoundTag ct) {
                sb.append(prettyPrintNbt(ct, childIndent));
            } else {
                sb.append(value);
            }

            sb.append('\n');
        }

        sb.append(indent).append('}');
        return sb.toString();
    }


}
