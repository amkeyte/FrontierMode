package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Forge's half of {@link RealEventHealthCheck}: its events on {@code MobSpawnEvent.FinalizeSpawn} and
 * {@code VillagerTradesEvent}, and the raw listeners on Forge's bus that count the same firings. The check is shared.
 */
@PlatformBound(reason = "health-probe")
public final class RealEventIngress {

    private RealEventIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:real_events");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());

        // The raw controls. receiveCancelled, like the ingress's own listener below, so a listener
        // that cancels a firing cannot make one of the two count it and the other not.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, MobSpawnEvent.FinalizeSpawn.class,
                RealEventIngress::onRawFinalizeSpawn);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, VillagerTradesEvent.class,
                RealEventIngress::onRawTrades);
    }

    private static void onRawFinalizeSpawn(MobSpawnEvent.FinalizeSpawn e) {
        RealEventHealthCheck.onRawFinalizeSpawn();
    }

    private static void onRawTrades(VillagerTradesEvent e) {
        RealEventHealthCheck.onRawTrades();
    }

    static final class ForgeSpawnFinalized extends ModuleSignal<MobSpawnEvent.FinalizeSpawn, Void>
            implements RealEventHealthCheck.SpawnFinalized {
        ForgeSpawnFinalized(MobSpawnEvent.FinalizeSpawn e) {
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
        ingress.receiveCancelled().translate(MobSpawnEvent.FinalizeSpawn.class,
                RealEventHealthCheck.SpawnFinalized.class, ForgeSpawnFinalized::new);
        ingress.translate(VillagerTradesEvent.class, RealEventHealthCheck.TradesLoaded.class, ForgeTradesLoaded::new);
        ingress.consumers(RealEventHealthCheck.consumers());
        return ingress;
    }
}
