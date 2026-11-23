package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.Optional;

/**
 * {@code neoforge-1.21.1}'s ingress for the bosstell module: NeoForge's {@link ItemEntityPickupEvent.Pre} translated to
 * {@link CompassPickup}, with {@link BossTellConsumers} installed. That event is not cancelable: it carries a
 * three-state pickup flag, which the class binds in its constructor. The mod entry registers {@link #build()} through
 * a lambda, so this class loads when the server side boots.
 */
public final class BossTellIngress {

    private BossTellIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BOSSTELL_INGRESS);
        ingress.translate(ItemEntityPickupEvent.Pre.class, CompassPickup.class, BossTellIngress::resolvePicker,
                ForgeCompassPickup::new);
        ingress.consumers(BossTellConsumers.declare());
        return ingress;
    }

    /**
     * The cheap filter: a pickup fires for every item a player walks over, so only a compass stack gets an event
     * built, with the picking player as its context.
     */
    private static Optional<Player> resolvePicker(ItemEntityPickupEvent.Pre event) {
        return CompassPickup.passes(event.getItemEntity().getItem()) ? Optional.of(event.getPlayer()) : Optional.empty();
    }

    /** {@link CompassPickup} over NeoForge's pre-pickup event, binding its pickup flag as the result. */
    static final class ForgeCompassPickup extends ModuleSignal<ItemEntityPickupEvent.Pre, Player>
            implements CompassPickup {

        private final ItemStack stack;

        ForgeCompassPickup(ItemEntityPickupEvent.Pre forgeEvent, Player picker) {
            super(forgeEvent, picker);
            this.stack = forgeEvent.getItemEntity().getItem();
            bindResult(() -> switch (forgeEvent.canPickup()) {
                case TRUE -> Verdict.ALLOW;
                case FALSE -> Verdict.DENY;
                case DEFAULT -> Verdict.DEFAULT;
            }, verdict -> forgeEvent.setCanPickup(switch (verdict) {
                case ALLOW -> TriState.TRUE;
                case DENY -> TriState.FALSE;
                case DEFAULT -> TriState.DEFAULT;
            }));
        }

        @Override
        public ItemStack stack() {
            return stack;
        }

        @Override
        public void blockPickup() {
            deny();
        }
    }
}
