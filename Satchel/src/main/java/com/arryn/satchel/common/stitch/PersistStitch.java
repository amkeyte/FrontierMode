package com.arryn.satchel.common.stitch;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

/**
 * Capability stitch indicating that a scope supports persistence.
 *
 * <p>
 * This stitch was originally intended to declare persistence capability.
 * It is currently dead and has been superseded by {@link SatchelBundle#required()}
 * and {@link JigPolicies.Capabilities}.
 * </p>
 *
 * <p>
 * <strong>Deprecated:</strong> This interface is no longer used for persistence.
 * Persistence is now handled through {@link SatchelBundle#required()} and
 * {@link JigPolicies.Capabilities}.
 * </p>
 *
 * <p>
 * <strong>Implementation Note:</strong> This interface is not intended to be
 * implemented directly. Instead, use {@link SatchelBundle#required()} to declare
 * persistence requirements.
 * </p>
 *
 * <p>
 * <strong>Hard Sidedness:</strong> This interface requires the use of the server
 * side. Any attempt to use this interface on the client side will result in an
 * exception.
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
