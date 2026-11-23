package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;

import java.util.Optional;

/**
 * {@code forge-1.20.1}'s ingress for the Keeper module (wiki/frontiermode/architecture/keeper.md § Class map): Forge's
 * pre-conversion and change-target events translated to {@link KeeperConversion} and {@link KeeperTargetChange}, with
 * {@link KeeperConsumers} installed. The mod entry registers {@link #build()} through a lambda, so this class loads
 * when the server side boots.
 */
public final class KeeperIngress {

    private KeeperIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.KEEPER_INGRESS);
        ingress.consumers(KeeperConsumers.declare());
        ingress.translate(LivingConversionEvent.Pre.class, KeeperConversion.class, KeeperIngress::resolveConvertingBoss,
                ForgeKeeperConversion::new);
        ingress.translate(LivingChangeTargetEvent.class, KeeperTargetChange.class, KeeperIngress::resolveTargetedBoss,
                ForgeKeeperTargetChange::new);
        return ingress;
    }

    /** The boss's mob scope, for the mob that would convert; empty when it is not a boss. */
    private static Optional<ScopeInfo> resolveConvertingBoss(LivingConversionEvent.Pre event) {
        return BossMarkers.bossScope(event.getEntity());
    }

    /** The boss's mob scope, for the new target; empty when it is not a boss (or the target is cleared). */
    private static Optional<ScopeInfo> resolveTargetedBoss(LivingChangeTargetEvent event) {
        return BossMarkers.bossScope(event.getNewTarget());
    }

    /** {@link KeeperConversion} over Forge's pre-conversion event. */
    static final class ForgeKeeperConversion extends ModuleSignal<LivingConversionEvent.Pre, ScopeInfo>
            implements KeeperConversion {

        ForgeKeeperConversion(LivingConversionEvent.Pre forgeEvent, ScopeInfo bossScope) {
            super(forgeEvent, bossScope);
        }

        @Override
        public void cancel() {
            nativeEvent().setCanceled(true);
        }
    }

    /** {@link KeeperTargetChange} over Forge's change-target event. */
    static final class ForgeKeeperTargetChange extends ModuleSignal<LivingChangeTargetEvent, ScopeInfo>
            implements KeeperTargetChange {

        ForgeKeeperTargetChange(LivingChangeTargetEvent forgeEvent, ScopeInfo bossScope) {
            super(forgeEvent, bossScope);
        }

        @Override
        public LivingEntity changingMob() {
            return nativeEvent().getEntity();
        }

        @Override
        public void cancel() {
            nativeEvent().setCanceled(true);
        }
    }
}
