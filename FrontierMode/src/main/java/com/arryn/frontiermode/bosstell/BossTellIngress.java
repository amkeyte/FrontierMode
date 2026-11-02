package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.bosstell.common.CompassAttunement;
import com.arryn.frontiermode.bosstell.common.fixture.BossTellFixture;
import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.frontiermode.keeper.KeeperCreated;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * The holder class for the bosstell ingress: the compass pickup, and the Tell module's consumer of the Keeper's
 * {@link KeeperCreated}. {@link BossTellModule#init()} registers {@link #build()} through a lambda, so this class
 * loads when the server side boots.
 */
final class BossTellIngress {

    private BossTellIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BOSSTELL_INGRESS);
        ingress.translate(EntityItemPickupEvent.class, BossTellIngress::resolvePicker, CompassPickup::new);
        ingress.listenForAll(CompassPickup.class, BossTellIngress::onPickup);
        ingress.listenForAll(KeeperCreated.class, BossTellIngress::onKeeperCreated);
        return ingress;
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
     * The cheap filter: a pickup fires for every item a player walks over, so only a compass stack
     * gets an event built, with the picking player as its context.
     */
    private static Optional<Player> resolvePicker(EntityItemPickupEvent event) {
        if (event.getItem().getItem().getItem() instanceof AmethystCompassItem) {
            return Optional.of(event.getEntity());
        }
        return Optional.empty();
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
