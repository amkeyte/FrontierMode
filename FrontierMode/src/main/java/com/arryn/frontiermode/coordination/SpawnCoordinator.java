package com.arryn.frontiermode.coordination;

import com.arryn.frontiermode.border.BorderModule;
import com.arryn.frontiermode.boss.BossModule;
import com.arryn.frontiermode.feral.FeralAPI;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

/**
 * The one consumer of {@link MobSpawnFinalizing}, fanning out to the modules that each need a turn on
 * a spawn, in a fixed order: Feral's Wilds Slime/MagmaCube deny first, then Boss (sentinel
 * conversion), Border (Sick Wildlife's density roll), and Feral (its density roll). The fan-out is
 * right to exist -- one place that states the order -- but it depends on all three modules and is
 * depended on by none, so it lives here, not in any of them. Each module's own decision logic stays
 * inside that module; this only calls them. See sentinels.md, "Hooking the spawn event", and
 * coordination.md.
 *
 * <p>The module keeps no state: the ingress is registered server side from {@link SpawnIngress}.
 */
public final class SpawnCoordinator {

    private SpawnCoordinator() {
    }

    public static void init() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> SpawnIngress.build());
    }

    /**
     * Denies via {@link MobSpawnFinalizing#denySpawn()}, not a cancel: cancelling the native event only
     * skips {@code Mob#finalizeSpawn}'s initialization and still lets the entity spawn.
     */
    static void onFinalizing(MobSpawnFinalizing spawn) {
        // The event is server-only by construction; the check spares a raw cast.
        //noinspection resource
        if (!(spawn.level() instanceof ServerLevel level)) {
            return;
        }
        Mob mob = spawn.mob();

        OUT.debug("[SpawnCoordinator] finalizing " + describe(mob, spawn.spawnType(), level));

        if (FeralAPI.shouldDenySpawn(mob, level)) {
            spawn.denySpawn();
            return;
        }

        BossModule.onMobSpawnFinalize(mob, level);
        BorderModule.onMobSpawnFinalize(mob, level);
        FeralAPI.onMobSpawnFinalize(mob, level);
    }

    private static String describe(Mob mob, MobSpawnType spawnType, Level level) {
        return "mob=" + EntityType.getKey(mob.getType())
                + " spawnType=" + spawnType
                + " level=" + level.dimension().location()
                + " thread=" + Thread.currentThread().getName();
    }
}
