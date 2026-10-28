package com.arryn.satchel.common.persistence;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.net.S2cBundleParcel;
import com.arryn.satchel.common.net.SatchelNetwork;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Terminal parcel egress emitter.
 *
 * <p>
 * Server-only. End-of-line for bundle network egress.
 */
public final class ParcelEgressSink {

    private final ScopeInfoImpl scopeInfo;
    private final BundleKey<?> key;

    private ParcelEgressSink(
            ScopeInfoImpl scopeInfo,
            BundleKey<?> key
    ) {
        this.scopeInfo = scopeInfo;
        this.key = key;
    }

    /**
     * Create an emitter for a specific bundle within a scope.
     */
    public static ParcelEgressSink forBundle(
            ScopeInfoImpl info,
            BundleKey<?> key
    ) {
        Satchel.requireServer();
        return new ParcelEgressSink(info, key);
    }

    /**
     * Emits a parcel containing the given bundle snapshot. Picks the distributor by scope kind
     * (SAT_063) -- {@link PlayerScope} goes out per-player via {@link
     * SatchelNetwork#sendToPlayer}, everything else still goes out per-dimension via {@link
     * SatchelNetwork#send} ({@link com.arryn.satchel.common.jig.level.LevelScope}-only, by that
     * method's own doc; {@code MobScope} networking has no distribution path built at all yet --
     * see the wiki's Networking § Known gaps section -- so nothing routes a {@code MobScope}
     * through here today).
     */
    public void emit(CompoundTag snapshot) {
        S2cBundleParcel parcel = new S2cBundleParcel(
                scopeInfo.scopeId(),
                key.id,
                key.name,
                snapshot
        );

        if (scopeInfo.scope() instanceof PlayerScope) {
            SatchelNetwork.sendToPlayer(scopeInfo, parcel);
        } else {
            SatchelNetwork.send(scopeInfo, parcel);
        }
    }

    /**
     * Emits the snapshot to one player only (SAT_144): the arrival push of a level scope's bundle to a
     * player who has just joined its audience. A player scope's bundle already goes to its one player
     * through {@link #emit}.
     */
    public void emitTo(ServerPlayer player, CompoundTag snapshot) {
        S2cBundleParcel parcel = new S2cBundleParcel(
                scopeInfo.scopeId(),
                key.id,
                key.name,
                snapshot
        );

        SatchelNetwork.sendToPlayer(player, scopeInfo, parcel);
    }
}
