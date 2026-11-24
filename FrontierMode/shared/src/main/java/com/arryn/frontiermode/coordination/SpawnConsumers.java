package com.arryn.frontiermode.coordination;

import com.arryn.frontiermode.coordination.server.SpawnCoordinator;
import com.arryn.satchel.common.ingress.GlobalConsumers;

/**
 * The spawn coordination module's global consumers, written once for both lines: the one consumer of
 * {@link MobSpawnFinalizing}, which fans a spawn out to Boss, Border and Feral in a fixed order. Each line's
 * {@code SpawnIngress} installs these.
 */
public final class SpawnConsumers {

    private SpawnConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(MobSpawnFinalizing.class, SpawnCoordinator::onFinalizing);
        return declared;
    }
}
