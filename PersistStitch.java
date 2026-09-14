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
 * a dead mechanism, implemented by {@code LevelScope}, {@code MobScope}, and
 * {@code PlayerScope} as hardcoded {@code Optional.empty()} stubs. No part of the codebase
 * ever calls {@code from(...)} or {@code loadBundleTag(...)}.
 * </p>
 *
 * <p>
 * The real, currently-active mechanism for declaring persistence capability is
 * {@code SatchelBundle.required()} / {@code JigPolicies.Capabilities}. This interface
 * has been superseded and is flagged for likely future removal (see SAT_053).
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
