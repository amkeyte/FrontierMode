package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * A mob of a kind a boss can have as its body has died: the module's event for the loader's living-death event, on both
 * lines (boss.md § What counts as a defeat). Its context is the mob, not a scope: a boss's body spends most of its life
 * with no mob scope attached, so a death is routine in that window and a scoped event would bounce it. Each line's
 * resolver passes only an entity whose type is some boss's body ({@link BossMarkers#isEligible}); {@link
 * BossJigHandlers#onBossDied} looks the mob up in the roster and ignores one that is not a boss. The ingress is
 * {@code SERVER}, so a death on the client's thread never reaches it.
 *
 * <p>It offers no veto.
 */
public interface BossDied extends ModuleEvent<Mob> {

    /** The mob that died. */
    default Mob mob() {
        return context();
    }

    /** The level it died in. */
    default Level level() {
        return mob().level();
    }
}
