package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Event handlers for {@code BOSS_JIG}: defeat detection.
 */
final class BossJigHandlers {

    private BossJigHandlers() {
    }

    /**
     * Hands a tracked boss's death to its {@link Boss}. The death event stays the entry because credit needs
     * its damage source; the {@code Boss} settles the death once, asks its kind whether it is a defeat, and
     * runs the defeat or the loss (boss.md § What counts as a defeat).
     *
     * <p>A single physical death can fire more than one {@code LivingDeathEvent} (a Forge/vanilla
     * quirk -- multiple queued damage instances in the same tick). {@code markDefeated()} and
     * {@code dematerialize()} refuse the second; a duplicate death event is a clean no-op.
     */
    static void onMobDied(MobDied event) {
        Mob mob = (Mob) event.forgeEvent().getEntity();
        Level level = event.level();
        OUT.debug("[Boss] onMobDied: received for " + Ids.shortId(event.uuid()));

        Optional<Boss> bossOpt = BossAPI.roster(level).flatMap(roster -> roster.bossFor(mob.getUUID()));
        if (bossOpt.isEmpty()) {
            return;
        }

        bossOpt.get().onDeath(mob);
    }
}
