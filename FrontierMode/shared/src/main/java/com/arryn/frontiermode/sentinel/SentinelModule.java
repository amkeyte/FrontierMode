package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.core.EntityScales;
import com.arryn.frontiermode.sentinel.server.SentinelPresence;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;

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
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> SentinelJigConfig.build());
        EntityScales.register(new SentinelScaleSource());
    }
}
