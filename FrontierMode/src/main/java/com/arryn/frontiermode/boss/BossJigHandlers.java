package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

/**
 * Event handlers for {@code BOSS_JIG}: tick and defeat detection.
 */
final class BossJigHandlers {

    private BossJigHandlers() {
    }

    static void onTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BOSS_JIG.equals(info.jigKey())) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        // BOSS_JIG is BOTH, so this handler is invoked on a client too; the server-only work below has not
        // moved off it yet (boss nodes B to G), and its fixture mutations call Satchel.requireServer().
        BossBundle bundle = info.getOrCreate(FrontierKeys.BOSS_BUNDLE);

        SentinelJigHandlers.onBossTick(level, bundle);
        if (level instanceof ServerLevel serverLevel) {
            // Alerts whose window has lapsed are let go: a per-level Sentinel call, not a per-boss one.
            bundle.sentinels().ifPresent(sentinels -> sentinels.releaseLapsedAlerts(serverLevel));
        }
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
