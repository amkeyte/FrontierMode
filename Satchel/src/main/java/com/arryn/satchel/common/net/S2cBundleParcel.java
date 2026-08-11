package com.arryn.satchel.common.net;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class S2cBundleParcel {

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

    public static void handle(
            S2cBundleParcel packet,
            Supplier<NetworkEvent.Context> ctx
    ) {
        try {
            ParcelInbox.enqueue(packet);
        } catch (Throwable t) {
            OUT.TRACE().log("[Satchel] Failed to enqueue S2cBundleParcel");
        } finally {
            ctx.get().setPacketHandled(true);
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
