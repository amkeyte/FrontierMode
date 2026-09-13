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
 * This interface was originally intended to declare that a scope supports persistence,
 * providing access to persisted bundle state for hydration. However, it is currently
 * dead, implemented as a hardcoded `Optional.empty()` stub in `LevelScope`, `MobScope`,
 * and `PlayerScope`. It has been superseded by `SatchelBundle.required()` /
 * `JigPolicies.Capabilities`, which is the real, currently-active mechanism for declaring
 * persistence capability. This interface is flagged for likely future removal (see SAT_053)
 * but has not been deleted yet, so it should not be treated as live documentation of
 * current behavior.
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
