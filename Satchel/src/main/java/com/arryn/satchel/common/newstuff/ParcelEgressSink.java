package com.arryn.satchel.common.newstuff;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.net.S2cBundleParcel;
import com.arryn.satchel.common.net.SatchelNetwork;

import net.minecraft.nbt.CompoundTag;

/**
 * Terminal parcel egress emitter.
 *
 * <p>
 * Server-only. End-of-line for bundle network egress.
 */
public final class ParcelEgressSink {

    private final ScopeInfo scopeInfo;
    private final BundleKey<?> key;

    private ParcelEgressSink(
            ScopeInfo scopeInfo,
            BundleKey<?> key
    ) {
        this.scopeInfo = scopeInfo;
        this.key = key;
    }

    /**
     * Create an emitter for a specific bundle within a scope.
     */
    public static ParcelEgressSink forBundle(
            ScopeInfo info,
            BundleKey<?> key
    ) {
        Satchel.requireServer();
        return new ParcelEgressSink(info, key);
    }

    /**
     * Emits a parcel containing the given bundle snapshot.
     */
    public void emit(CompoundTag snapshot) {
        S2cBundleParcel parcel = new S2cBundleParcel(
                scopeInfo.scopeId(),
                key.id,
                key.name,
                snapshot
        );

        SatchelNetwork.send(scopeInfo, parcel);
    }
}
