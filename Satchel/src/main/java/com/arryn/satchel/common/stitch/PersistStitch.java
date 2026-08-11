package com.arryn.satchel.common.stitch;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Capability stitch indicating that a scope supports persistence.
 *
 * <p>
 * This stitch is ingress-only. It provides access to persisted
 * bundle state for hydration. Egress (saving) is handled elsewhere.
 * </p>
 */
public interface PersistStitch {

    /**
     * Loads persisted NBT for the given bundle key, if present.
     */
    Optional<CompoundTag> loadBundleTag(BundleKey<?> key);

    // ---------------------------------------------------------------------
    // Capability access
    // ---------------------------------------------------------------------

    static Optional<PersistStitch> from(SatchelScope scope) {
        if (!(scope instanceof PersistStitch stitch)) {
            return Optional.empty();
        }

        // Hard sidedness assertion at capability boundary
        Satchel.requireServer();

        return Optional.of(stitch);
    }
}
