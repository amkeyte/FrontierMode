package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.UUID;

/**
 * NeoForge's half of {@link HotEventHealthCheck}: its two events on {@code LivingIncomingDamageEvent}, the stage before armor
 * where the hurt interfaces fire. The check is shared.
 */
public final class HotEventIngress {

    private HotEventIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:hot_events");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
    }

    static final class ForgeHurtNever extends ModuleSignal<LivingIncomingDamageEvent, Integer>
            implements HotEventHealthCheck.HurtNever {
        ForgeHurtNever(LivingIncomingDamageEvent e, Integer context) {
            super(e, context);
            HotEventHealthCheck.onNeverBuilt();
        }
    }

    static final class ForgeHurtTagged extends ModuleSignal<LivingIncomingDamageEvent, UUID>
            implements HotEventHealthCheck.HurtTagged {
        ForgeHurtTagged(LivingIncomingDamageEvent e, UUID context) {
            super(e, context);
            HotEventHealthCheck.onTaggedBuilt();
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        ingress.receiveCancelled().translate(LivingIncomingDamageEvent.class, HotEventHealthCheck.HurtNever.class,
                e -> HotEventHealthCheck.resolveNothing(), ForgeHurtNever::new);
        ingress.receiveCancelled().translate(LivingIncomingDamageEvent.class, HotEventHealthCheck.HurtTagged.class,
                e -> HotEventHealthCheck.resolveTagged(e.getEntity()), ForgeHurtTagged::new);
        ingress.consumers(HotEventHealthCheck.consumers());
        return ingress;
    }
}
