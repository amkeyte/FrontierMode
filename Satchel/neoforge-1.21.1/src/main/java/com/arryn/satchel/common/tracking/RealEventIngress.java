package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.platform.PlatformBound;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.bus.api.EventPriority;

/**
 * NeoForge's half of {@link RealEventHealthCheck}: its events on {@code FinalizeSpawnEvent} and
 * {@code VillagerTradesEvent}, and the raw listeners on NeoForge's bus that count the same firings. The check is shared.
 */
@PlatformBound(reason = "health-probe")
public final class RealEventIngress {

    private RealEventIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:real_events");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());

        // The raw controls. receiveCancelled, like the ingress's own listener below, so a listener
        // that cancels a firing cannot make one of the two count it and the other not.
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, FinalizeSpawnEvent.class,
                RealEventIngress::onRawFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, VillagerTradesEvent.class,
                RealEventIngress::onRawTrades);
    }

    private static void onRawFinalizeSpawn(FinalizeSpawnEvent e) {
        RealEventHealthCheck.onRawFinalizeSpawn();
    }

    private static void onRawTrades(VillagerTradesEvent e) {
        RealEventHealthCheck.onRawTrades();
    }

    static final class ForgeSpawnFinalized extends ModuleSignal<FinalizeSpawnEvent, Void>
            implements RealEventHealthCheck.SpawnFinalized {
        ForgeSpawnFinalized(FinalizeSpawnEvent e) {
            super(e, null);
        }
    }

    static final class ForgeTradesLoaded extends ModuleSignal<VillagerTradesEvent, Void>
            implements RealEventHealthCheck.TradesLoaded {
        ForgeTradesLoaded(VillagerTradesEvent e) {
            super(e, null);
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        ingress.receiveCancelled().translate(FinalizeSpawnEvent.class,
                RealEventHealthCheck.SpawnFinalized.class, ForgeSpawnFinalized::new);
        ingress.translate(VillagerTradesEvent.class, RealEventHealthCheck.TradesLoaded.class, ForgeTradesLoaded::new);
        ingress.consumers(RealEventHealthCheck.consumers());
        return ingress;
    }
}
