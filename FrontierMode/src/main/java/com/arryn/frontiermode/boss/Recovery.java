package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Ticking;
import net.minecraft.world.entity.Mob;

/**
 * A hurt boss heals the way the vanilla Wither heals itself (boss.md § The Recovery ability). Once a second of the
 * body's own ticks, a living body below its maximum health heals its maximum divided by the seconds from empty to
 * full, a setting the kind passes at subscribe (the Wither's own ratio is 300: 1 health a second on a maximum of
 * 300). Every body therefore takes about as long to recover from empty as the Wither does.
 *
 * <p>It runs only while the body ticks, and nothing catches an unloaded body up, so a body in an unloaded chunk is
 * as hurt as it was left. Each point it heals comes off the damage tally on the {@link Boss}, never below zero:
 * {@code Recovery} is the only thing that heals a boss, so it makes the subtraction itself. It keeps no state of
 * its own. Server only, so the kind subscribes it under {@code Satchel.isServer()}.
 */
public final class Recovery implements Ticking {

    // The Wither's own cadence: once a second.
    private static final int INTERVAL_TICKS = 20;

    private final Boss boss;
    private final int secondsToFull;

    public Recovery(Boss boss, int secondsToFull) {
        this.boss = boss;
        this.secondsToFull = secondsToFull;
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob mob = scope.mob();
        if (mob.tickCount % INTERVAL_TICKS != 0 || !mob.isAlive()) {
            return;
        }
        float before = mob.getHealth();
        float max = mob.getMaxHealth();
        if (before >= max) {
            return;
        }
        mob.heal(max / secondsToFull);
        // What the heal event left, not what was asked for: the tally gives back exactly what came back.
        boss.removePlayerDamage(mob.getHealth() - before);
    }
}
