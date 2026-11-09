package com.arryn.frontiermode.coordination;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

/**
 * The holder class for the spawn coordination ingress. {@link SpawnCoordinator#init()} registers
 * {@link #build()} through a lambda, so this class loads when the server side boots.
 */
final class SpawnIngress {

    private SpawnIngress() {
    }

    /**
     * Unscoped, so no resolver: nothing filters a spawn before the event is built, and the one consumer
     * is the module's only one. Forge listener priority and bus priority stay at normal.
     */
    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.SPAWN_COORDINATION);
        ingress.translate(MobSpawnEvent.FinalizeSpawn.class, MobSpawnFinalizing::new);
        ingress.listenForAll(MobSpawnFinalizing.class, SpawnCoordinator::onFinalizing);
        return ingress;
    }
}
