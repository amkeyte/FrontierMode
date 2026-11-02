package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.server.BorderPathGrown;
import com.arryn.frontiermode.boss.Boss;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * The holder class for the Keeper's ingress (wiki/frontiermode/architecture/keeper.md § Class map): one consumer of
 * Border's {@link BorderPathGrown}, which marks the new Border pending attach, one of the Info report's
 * {@link InfoRequest}, which adds the {@code keeper:} line, and the two module events the Keeper's mob abilities hear,
 * {@link KeeperConversion} and {@link KeeperTargetChange}. {@link KeeperModule#init()} registers
 * {@link #build()} through a lambda, so this class loads when the server side boots.
 */
final class KeeperIngress {

    private KeeperIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.KEEPER_INGRESS);
        ingress.listenForAll(BorderPathGrown.class, KeeperIngress::onPathGrown);
        ingress.listenForAll(InfoRequest.class, KeeperIngress::onInfo);
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

    /**
     * Registers the Keeper answer; the work happens in the supplier, once per report. The {@code keeper:} line is for a
     * Keeper's mob (found through the roster) or its {@code BossRecord} (keeper.md § Info lines), and left out for any
     * other subject.
     */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Keeper] InfoRequest reached its consumer");

        ServerLevel level = request.level();
        for (Object subject : request.subjects()) {
            if (subject instanceof Entity entity) {
                request.add(subject, "keeper", () -> keeperLine(level,
                        BossAPI.roster(level).flatMap(roster -> roster.bossFor(entity.getUUID())).map(Boss::bossId)));
            } else if (subject instanceof BossRecord record) {
                request.add(subject, "keeper", () -> keeperLine(level, Optional.of(record.bossId())));
            }
        }
    }

    /** The {@code keeper:} line, or null (no line) for a subject that is not a Keeper. */
    private static String keeperLine(ServerLevel level, Optional<UUID> bossId) {
        return bossId.flatMap(id -> KeeperAPI.keepers(level).flatMap(keepers -> keepers.get(id)))
                .map(keeper -> String.format("  keeper: layer %d, path position %d, body %s, border %s, platform %s",
                        keeper.layer(), KeeperAPI.pathPositionOf(keeper),
                        keeper.body().isEmpty() ? "-" : keeper.body(),
                        keeper.borderId().map(Ids::shortId).orElse("none"),
                        keeper.platformBuilt()
                                ? keeper.platformPos().getX() + ", " + keeper.platformPos().getY() + ", "
                                        + keeper.platformPos().getZ()
                                : "-"))
                .orElse(null);
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
