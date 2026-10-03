package com.arryn.frontiermode.coordination;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderModule;
import com.arryn.frontiermode.boss.BossModule;
import com.arryn.frontiermode.feral.FeralModule;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.MobSpawnFinalizeSignal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * The one subscriber to {@link MobSpawnFinalizeSignal}, fanning out to the modules that each need a
 * turn on a spawn, in a fixed order: Feral's Wilds Slime/MagmaCube deny first, then Boss (sentinel
 * conversion), Border (Sick Wildlife's density roll), and Feral (its density roll). The fan-out is
 * right to exist -- one place that states the order -- but it depends on all three modules and is
 * depended on by none, so it lives here, not in any of them. Each module's own decision logic stays
 * inside that module; this only calls them. See sentinels.md, "Hooking the spawn event".
 *
 * <p>Registered like Feral's tick sources: its own tick-free {@code JigConfig} with an empty bundle
 * schema, only where installation happens to occur -- a bare signal isn't scoped to it, so the
 * handler carries no jig-key guard.
 */
public final class SpawnCoordinator {

    private SpawnCoordinator() {
    }

    public static void init() {
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(MobSpawnFinalizeSignal.class, SpawnCoordinator::onMobSpawnFinalize)
                        .build();

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.SPAWN_COORDINATOR_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults())
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    /**
     * {@link MobSpawnFinalizeSignal#denySpawn()}, not {@code cancel()}: canceling the signal only skips
     * {@code Mob#finalizeSpawn}'s initialization and still lets the entity spawn.
     */
    private static void onMobSpawnFinalize(MobSpawnFinalizeSignal signal) {
        // The signal is server-only by construction; the check spares a raw cast.
        //noinspection resource
        if (!(signal.level() instanceof ServerLevel level)) {
            return;
        }
        Mob mob = signal.mob();

        if (FeralModule.shouldDenySpawn(mob, level)) {
            signal.denySpawn();
            return;
        }

        BossModule.onMobSpawnFinalize(mob, level);
        BorderModule.onMobSpawnFinalize(mob, level);
        FeralModule.onMobSpawnFinalize(mob, level);
    }
}
