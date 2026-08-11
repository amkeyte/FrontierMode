package com.arryn.satchel.common.net;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.util.out.OUT;

import java.util.*;

public final class ParcelInbox {

    private static final Map<UUID, Map<UUID, S2cBundleParcel>> INBOX =
            new HashMap<>();

    private ParcelInbox() {}

    public static synchronized void enqueue(S2cBundleParcel parcel) {
        Satchel.requireClient();

        INBOX
                .computeIfAbsent(parcel.scopeId(), id -> new HashMap<>())
                .put(parcel.bundleId(), parcel);

        OUT.TRACE().log("[ParcelInbox] enqueued Parcel for bundle: " + parcel.bundleId());
    }
//
//    public static synchronized S2cBundleParcel poll(
//            UUID scopeId,
//            UUID bundleId
//    ) {
//        Map<UUID, S2cBundleParcel> parcels =
//                INBOX.get(scopeId);
//        if (parcels == null) return null;
//
//        OUT.TRACE().log("[ParcelInbox] polled Parcel for bundle: " + bundleId);
//
//        return parcels.remove(bundleId);
//    }

    public static synchronized Collection<S2cBundleParcel> drainForScope(
            UUID scopeId
    ) {
        Map<UUID, S2cBundleParcel> parcels =
                INBOX.remove(scopeId);
        if (parcels == null) return List.of();

        OUT.TRACE().log("[ParcelInbox] draining "+ parcels.size() + " Parcels for scopeInfo: " + scopeId);

        return parcels.values();
    }
}
