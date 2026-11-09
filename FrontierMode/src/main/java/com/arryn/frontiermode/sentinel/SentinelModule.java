package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.core.EntityScales;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * Entry point for the Sentinel module (wiki/frontiermode/architecture/sentinels.md): hostile mobs converted at spawn near a
 * Keeper. Depends on Keeper, so it initializes after {@code KeeperModule.init()}, never before; {@code boss} and
 * {@code keeper} know nothing of it. The module is a root: registration lines only, and the one entry point
 * {@code SpawnCoordinator} calls.
 *
 * <p>A Sentinel is a record on the mob ({@link SentinelData}); the ones present in the level are
 * {@link SentinelPresence}, a Trinket on {@code SENTINEL_JIG}; the synced record clients read is
 * {@code SentinelFixture} in that jig's bundle. Nothing is persisted.
 */
public final class SentinelModule {

    private SentinelModule() {
    }

    public static void init() {
        OUT.info("[Sentinel] init(): Module initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> SentinelIngress.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> SentinelJigConfig.build());
        EntityScales.register(new SentinelScaleSource());
    }

    /**
     * Sentinel conversion's entry point: called by {@code SpawnCoordinator}, the one consumer of
     * {@code MobSpawnFinalizing}, in its fixed order after Feral's Slime/MagmaCube deny.
     *
     * <p>Never cancels the spawn either way -- Sentinels is a modifier on an already-happening
     * spawn, not a summon. "Standby, don't crash": a {@link SentinelPresence} that isn't
     * resolvable yet (Satchel not ready, scope not known) is a silent no-op -- the vanilla spawn
     * proceeds unmodified either way. {@code level} is already confirmed a real {@link ServerLevel}
     * by the caller.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        SentinelPresence.of(level).ifPresent(presence -> presence.onMobSpawnFinalize(mob, level));
    }
}
