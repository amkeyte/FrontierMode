package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.server.BorderPathGrown;
import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;

import java.util.Optional;

/**
 * The holder class for the Keeper's ingress (wiki/frontiermode/architecture/keeper.md § Class map): one consumer of
 * Border's {@link BorderPathGrown}, which marks the new Border pending attach, and the two module events the Keeper's
 * mob abilities hear, {@link KeeperConversion} and {@link KeeperTargetChange}. {@link KeeperModule#init()} registers
 * {@link #build()} through a lambda, so this class loads when the server side boots.
 */
final class KeeperIngress {

    private KeeperIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.KEEPER_INGRESS);
        ingress.listenForAll(BorderPathGrown.class, KeeperIngress::onPathGrown);
        ingress.translate(LivingConversionEvent.Pre.class, KeeperIngress::resolveConvertingBoss, KeeperConversion::new);
        ingress.translate(LivingChangeTargetEvent.class, KeeperIngress::resolveTargetedBoss, KeeperTargetChange::new);
        return ingress;
    }

    /**
     * {@code pathGrow} advanced the path without a Keeper: the Border waits for {@code /boss attach}
     * (keeper.md § Boss-less path layers and attach). A Keeper fixture that is not resolvable yet is a silent
     * no-op, the "standby, don't crash" discipline: the grow itself already succeeded and is not rolled back.
     */
    private static void onPathGrown(BorderPathGrown event) {
        OUT.debug("[Keeper] BorderPathGrown reached its consumer");
        KeeperAPI.keepers(event.level()).ifPresent(keepers -> keepers.addPendingAttach(event.border().id()));
    }

    /** The boss's mob scope, for the mob that would convert; empty when it is not a boss. */
    private static Optional<ScopeInfo> resolveConvertingBoss(LivingConversionEvent.Pre event) {
        return bossScope(event.getEntity());
    }

    /** The boss's mob scope, for the new target; empty when it is not a boss (or the target is cleared). */
    private static Optional<ScopeInfo> resolveTargetedBoss(LivingChangeTargetEvent event) {
        return bossScope(event.getNewTarget());
    }

    // The cheap filter these events share: they fire for every entity, so a type no boss can be bounces first.
    private static Optional<ScopeInfo> bossScope(Entity entity) {
        if (entity == null || !BossMarkers.isEligible(entity)) {
            return Optional.empty();
        }
        return Scopes.of(FrontierKeys.BOSS_MOB_JIG, entity);
    }
}
