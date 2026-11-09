package com.arryn.frontiermode.siting;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import net.minecraftforge.event.level.LevelEvent;

/**
 * The holder class for the siting ingress. {@link SitingModule#init()} registers {@link #build()} through
 * a lambda, so this class loads when the server side boots.
 *
 * <p>Unscoped and with no resolver: the event fires once per world, so there is nothing to filter before
 * it is built, and its one consumer is the module's own.
 */
final class SitingIngress {

    private SitingIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.SITING_INGRESS);
        ingress.translate(LevelEvent.CreateSpawnPosition.class, WorldSpawnChoosing::new);
        ingress.listenForAll(WorldSpawnChoosing.class, StartingSiteHandlers::onWorldSpawnChoosing);
        return ingress;
    }
}
