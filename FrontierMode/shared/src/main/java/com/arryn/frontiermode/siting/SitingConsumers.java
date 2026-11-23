package com.arryn.frontiermode.siting;

import com.arryn.satchel.common.ingress.GlobalConsumers;

/**
 * The siting module's global consumers, written once for both lines: the one consumer of {@link WorldSpawnChoosing},
 * which runs the starting-site search. Each line's {@code SitingIngress} installs these.
 */
public final class SitingConsumers {

    private SitingConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(WorldSpawnChoosing.class, StartingSiteHandlers::onWorldSpawnChoosing);
        return declared;
    }
}
