package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.UUID;

/**
 * Forge's half of {@link CanaryEventHealthCheck}: the canary's hurt on {@code LivingHurtEvent} and the unscoped death
 * on {@code LivingDeathEvent}. The check is shared.
 */
public final class CanaryEventIngress {

    private CanaryEventIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:canary_events");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
    }

    static final class ForgeCanaryHurt extends ModuleSignal<LivingHurtEvent, ScopeInfo>
            implements CanaryEventHealthCheck.CanaryHurt {
        ForgeCanaryHurt(LivingHurtEvent e, ScopeInfo canary) {
            super(e, canary);
            CanaryEventHealthCheck.onHurtBuilt(canary);
        }

        @Override
        public float amount() {
            return nativeEvent().getAmount();
        }

        @Override
        public void setAmount(float amount) {
            nativeEvent().setAmount(amount);
        }
    }

    static final class ForgeCanaryNamespaceDied extends ModuleSignal<LivingDeathEvent, Void>
            implements CanaryEventHealthCheck.CanaryNamespaceDied {
        private final UUID mob;          // fact, copied
        private final boolean canary;    // fact, copied

        ForgeCanaryNamespaceDied(LivingDeathEvent e) {
            super(e, null);
            mob = e.getEntity().getUUID();
            canary = CanaryData.isCanary(e.getEntity());
        }

        @Override
        public UUID mob() {
            return mob;
        }

        @Override
        public boolean isCanary() {
            return canary;
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        ingress.translate(LivingHurtEvent.class, CanaryEventHealthCheck.CanaryHurt.class,
                e -> CanaryEventHealthCheck.resolveHurt(e.getEntity()), ForgeCanaryHurt::new);
        ingress.translate(LivingDeathEvent.class, CanaryEventHealthCheck.CanaryNamespaceDied.class,
                ForgeCanaryNamespaceDied::new);
        ingress.consumers(CanaryEventHealthCheck.consumers());
        return ingress;
    }
}
