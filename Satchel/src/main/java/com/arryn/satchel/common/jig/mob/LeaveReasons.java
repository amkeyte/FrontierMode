package com.arryn.satchel.common.jig.mob;

import com.arryn.satchel.common.jig.guts.LeaveReason;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Translates the vanilla removal reason into Satchel's {@link LeaveReason}, so the vanilla enum stays
 * inside Satchel's platform layer. Advisory by design (see {@code LeaveReason}).
 */
@PlatformBound(reason = "Reads a mob's vanilla removal reason; the vanilla enum stays here")
final class LeaveReasons {

    private LeaveReasons() {
    }

    /**
     * Why {@code mob} is leaving, from its removal reason; {@link LeaveReason#UNKNOWN} when none is
     * recorded.
     */
    static LeaveReason of(Mob mob) {
        Entity.RemovalReason reason = mob.getRemovalReason();
        if (reason == null) return LeaveReason.UNKNOWN;
        return switch (reason) {
            case KILLED -> LeaveReason.KILLED;
            case DISCARDED -> LeaveReason.DISCARDED;
            case CHANGED_DIMENSION -> LeaveReason.CHANGED_LEVEL;
            case UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER -> LeaveReason.UNLOADED;
            default -> LeaveReason.UNKNOWN;
        };
    }
}
