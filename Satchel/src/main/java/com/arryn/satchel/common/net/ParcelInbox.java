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

    /**
     * Removes and returns only the parcels for {@code ownedBundleIds} under {@code scopeId} --
     * parcels for any other bundle on the same scope stay queued for whichever engine actually
     * owns them (SAT_080). Every jig on a level shares that level's scope UUID but has its own
     * engine, so draining the whole scope here let whichever jig pulsed first consume, and drop,
     * every other jig's parcels.
     */
    public static synchronized Collection<S2cBundleParcel> drainForScope(
            UUID scopeId,
            Set<UUID> ownedBundleIds
    ) {
        Map<UUID, S2cBundleParcel> parcels = INBOX.get(scopeId);
        if (parcels == null) return List.of();

        List<S2cBundleParcel> drained = new ArrayList<>();
        var it = parcels.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (ownedBundleIds.contains(entry.getKey())) {
                drained.add(entry.getValue());
                it.remove();
            }
        }
        if (parcels.isEmpty()) INBOX.remove(scopeId);

        if (!drained.isEmpty()) {
            OUT.TRACE().log("[ParcelInbox] draining " + drained.size() + " Parcels for scopeInfo: " + scopeId);
        }

        return drained;
    }
}
