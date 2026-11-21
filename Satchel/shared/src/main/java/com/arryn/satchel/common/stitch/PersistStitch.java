package com.arryn.satchel.common.stitch;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.jig.player.PlayerScope;
import net.minecraft.nbt.CompoundTag;
import com.arryn.satchel.common.jig.config.JigPolicies;

import java.util.Optional;

/**
 * Capability stitch indicating that a scope supports persistence.
 *
 * <p>
 * Originally provided access to persisted bundle state for hydration.
 * Implemented by {@link LevelScope}, {@link MobScope}, and {@link
 * PlayerScope}, but all implementations are hardcoded stubs returning
 * {@code Optional.empty()}. Nothing in the codebase calls
 * {@link PersistStitch#from(SatchelScope)} or {@link #loadBundleTag(BundleKey)}. This
 * interface is dead code.
 * </p>
 *
 * <p>
 * Superseded by {@link SatchelBundle#required()} and {@link
 * JigPolicies.Capabilities}, which is the real, active mechanism for
 * declaring persistence capability. Flagged for removal per SAT_053 but
 * not yet deleted.
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
