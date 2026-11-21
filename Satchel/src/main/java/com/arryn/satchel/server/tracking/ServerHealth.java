package com.arryn.satchel.server.tracking;

import com.arryn.satchel.common.config.SatchelConfig;

/**
 * The SatchelHealth probes that need the server's own classes, registered after the shared ones in {@code
 * SatchelHealth.init()}: {@code SatchelMod} calls both. Common code cannot name them, so they register
 * themselves from here, under the same switch ({@link SatchelConfig#HEALTH_ENABLED}).
 */
public final class ServerHealth {

    private ServerHealth() {}

    public static void init() {
        if (!SatchelConfig.HEALTH_ENABLED.get()) return;

        SpawnGateHealthCheck.register();
        CanaryCleanupHealthCheck.register();
    }
}
