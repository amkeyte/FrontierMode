package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

import java.util.Optional;

/**
 * {@code forge-1.20.1}'s ingress for the bosstell module: Forge's {@link EntityItemPickupEvent} translated to
 * {@link CompassPickup}, with {@link BossTellConsumers} installed. The mod entry registers {@link #build()} through a
 * lambda, so this class loads when the server side boots.
 */
public final class BossTellIngress {

    private BossTellIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BOSSTELL_INGRESS);
        ingress.translate(EntityItemPickupEvent.class, CompassPickup.class, BossTellIngress::resolvePicker,
                ForgeCompassPickup::new);
        ingress.consumers(BossTellConsumers.declare());
        return ingress;
    }

    /**
     * The cheap filter: a pickup fires for every item a player walks over, so only a compass stack gets an event
     * built, with the picking player as its context.
     */
    private static Optional<Player> resolvePicker(EntityItemPickupEvent event) {
        return CompassPickup.passes(event.getItem().getItem()) ? Optional.of(event.getEntity()) : Optional.empty();
    }

    /** {@link CompassPickup} over Forge's item-pickup event, which can be canceled. */
    static final class ForgeCompassPickup extends ModuleSignal<EntityItemPickupEvent, Player> implements CompassPickup {

        private final ItemStack stack;

        ForgeCompassPickup(EntityItemPickupEvent forgeEvent, Player picker) {
            super(forgeEvent, picker);
            this.stack = forgeEvent.getItem().getItem();
        }

        @Override
        public ItemStack stack() {
            return stack;
        }

        @Override
        public void blockPickup() {
            nativeEvent().setCanceled(true);
        }
    }
}
