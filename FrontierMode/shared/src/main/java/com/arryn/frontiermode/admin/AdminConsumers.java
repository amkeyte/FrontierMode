package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.GlobalConsumers;

/**
 * The admin module's global consumers, written once for both lines: one for each of the three events that fire far more
 * often than an admin tool is involved ({@link InspectSwing}, {@link DeathTouchHit}, {@link SoulboundToss}). The two
 * scoped events, {@link SoulboundDeathDrops} and {@link SoulboundRespawn}, are heard by the stash Trinket on the player's
 * own host scope, so they have no global consumer. Each line's {@code AdminIngress} installs these.
 */
public final class AdminConsumers {

    private AdminConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(InspectSwing.class, AdminToolHandlers::onInspectSwing);
        declared.listenForAll(DeathTouchHit.class, AdminToolHandlers::onDeathTouchHit);
        declared.listenForAll(SoulboundToss.class, AdminToolHandlers::onSoulboundToss);
        return declared;
    }
}
