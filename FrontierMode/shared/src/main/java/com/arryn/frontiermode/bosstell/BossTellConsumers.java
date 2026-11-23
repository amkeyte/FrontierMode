package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.bosstell.common.CompassAttunement;
import com.arryn.frontiermode.bosstell.common.fixture.BossTellFixture;
import com.arryn.frontiermode.keeper.KeeperCreated;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.util.out.OUT;

import java.util.Optional;
import java.util.UUID;

/**
 * The Tell module's global consumers, written once for both lines: the consumer of {@link CompassPickup}, and the
 * consumer of the Keeper's {@link KeeperCreated}. Each line's {@code BossTellIngress} installs these.
 */
public final class BossTellConsumers {

    private BossTellConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(CompassPickup.class, BossTellConsumers::onPickup);
        declared.listenForAll(KeeperCreated.class, BossTellConsumers::onKeeperCreated);
        return declared;
    }

    /**
     * A Keeper was created for a Border (keeper.md § Keeper events): the Border gets its {@code "tell"} curve if it has
     * none. Idempotent, so a restart or a second creation is a no-op.
     */
    private static void onKeeperCreated(KeeperCreated created) {
        OUT.debug("[BossTell] KeeperCreated reached its consumer");
        BossTellFixture.createTellCurveIfAbsent(created.level(), created.borderId());
    }

    /**
     * "Blocked, never destroyed" for anyone but the compass's owner: cancels the pickup for any player
     * who is not the stack's recorded owner, leaving the compass where it lies for a later attempt by
     * the rightful owner, including from their own death drop. A stack with no attunement at all is
     * untouched.
     */
    private static void onPickup(CompassPickup pickup) {
        OUT.debug("[BossTell] CompassPickup reached its consumer for " + pickup.picker().getGameProfile().getName());

        Optional<UUID> ownerId = CompassAttunement.ownerId(pickup.stack());
        if (ownerId.isEmpty()) {
            return;
        }
        if (!ownerId.get().equals(pickup.picker().getUUID())) {
            pickup.blockPickup();
        }
    }
}
