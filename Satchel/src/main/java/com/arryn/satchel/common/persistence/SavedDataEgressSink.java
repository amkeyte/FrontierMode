package com.arryn.satchel.common.persistence;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * Terminal egress sink backed by Forge SavedData.
 *
 * <p>
 * This class is the end-of-line for persistence egress.
 * It is the ONLY place that knows about:
 * <ul>
 *   <li>ServerLevel</li>
 *   <li>SavedData</li>
 *   <li>Forge persistence semantics</li>
 * </ul>
 *
 * <p>
 * All sidedness is enforced at construction time.
 * Upstream code may assume correctness.
 */
public final class SavedDataEgressSink {

    private final BundleSavedData saved;

    private SavedDataEgressSink(BundleSavedData saved) {
        this.saved = saved;
    }

    /**
     * Creates an egress sink for the given bundle.
     * Always returns a sink; persistence is assumed to exist.
     */
    public static SavedDataEgressSink forBundle(
            ServerLevel level,
            BundleKey<?> key
    ) {
        // -----------------------------------------------------------------
        // Sidedness enforcement (terminal boundary)
        // -----------------------------------------------------------------
        Satchel.requireServer();

        BundleSavedData data =
                BundleSavedData.getForWrite(level, key);

        return new SavedDataEgressSink(data);
    }

    /**
     * Stores the given fixture snapshot and marks persistence dirty.
     */
    public void store(CompoundTag snapshot) {
        saved.setFixtures(snapshot);
        saved.markBundleDirty();
    }
}
