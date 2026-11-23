package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Forge's half of {@link MobJoinHealthCheck}: its event on {@code EntityJoinLevelEvent}, first among the listeners,
 * and the raw listener that cancels the probe's join last among them. The check is shared.
 */
@PlatformBound(reason = "health-probe")
public final class MobJoinIngress {

    private MobJoinIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:mob_join");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());

        // The spawn blocker: last among the listeners, so the module event has been resolved first.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class,
                MobJoinIngress::onRawJoin);
    }

    private static void onRawJoin(EntityJoinLevelEvent e) {
        if (MobJoinHealthCheck.refusesJoin(e.getEntity())) e.setCanceled(true);
    }

    static final class ForgeJoinedProbeMob extends ModuleSignal<EntityJoinLevelEvent, ScopeInfo>
            implements MobJoinHealthCheck.JoinedProbeMob {
        ForgeJoinedProbeMob(EntityJoinLevelEvent e, ScopeInfo scope) {
            super(e, scope);
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        // First among the listeners: the join is still open to cancellation when this resolver runs.
        ingress.priority(EventPriority.HIGHEST).translate(EntityJoinLevelEvent.class,
                MobJoinHealthCheck.JoinedProbeMob.class, e -> MobJoinHealthCheck.resolveJoin(e.getEntity()),
                ForgeJoinedProbeMob::new);
        ingress.consumers(MobJoinHealthCheck.consumers());
        return ingress;
    }
}
