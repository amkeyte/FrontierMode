package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Optional;
import java.util.UUID;

/**
 * The holder class for the boss module's ingress: one consumer of the Info report's {@link InfoRequest},
 * which adds the {@code boss:} line for an entity, and the two module events the mob abilities hear, {@link BossMount} and {@link BossDamaged}.
 * {@link BossModule#init()} registers {@link #build()} through a lambda, so this class loads when the
 * server side boots.
 */
final class BossIngress {

    private BossIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BOSS_INGRESS);
        ingress.listenForAll(InfoRequest.class, BossIngress::onInfo);
        ingress.translate(EntityMountEvent.class, BossIngress::resolveMountingBoss, BossMount::new);
        // Lowest, so the amount is what every other listener left.
        ingress.priority(EventPriority.LOWEST).translate(
                LivingDamageEvent.class, BossIngress::resolveDamagedBoss, BossDamaged::new);
        return ingress;
    }

    /** The boss's mob scope, for the entity mounting or the entity being mounted; empty when neither is a boss. */
    private static Optional<ScopeInfo> resolveMountingBoss(EntityMountEvent event) {
        return bossScope(event.getEntityMounting()).or(() -> bossScope(event.getEntityBeingMounted()));
    }

    private static Optional<ScopeInfo> resolveDamagedBoss(LivingDamageEvent event) {
        return bossScope(event.getEntity());
    }

    // The cheap filter these events share: they fire for every entity, so a type no boss can be bounces first.
    private static Optional<ScopeInfo> bossScope(Entity entity) {
        if (entity == null || !BossMarkers.isEligible(entity)) {
            return Optional.empty();
        }
        return Scopes.of(FrontierKeys.BOSS_MOB_JIG, entity);
    }

    /**
     * Registers the boss answers; the work happens in the suppliers, once per report. The {@code boss:} line is for
     * a boss's mob (found through the roster) or a {@code BossRecord}, and {@code -} for an entity that is not a boss.
     */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Boss] InfoRequest reached its consumer");

        ServerLevel level = request.level();
        for (Object subject : request.subjects()) {
            if (subject instanceof Entity entity) {
                request.add(subject, "boss", () -> "  boss: " + mobLine(level, entity));
            } else if (subject instanceof BossRecord record) {
                request.add(subject, "boss", () -> "  boss: " + recordLine(level, record.bossId()));
            }
        }
    }

    /** What the {@code Boss} knows about the mob that is its body, with its id and kind; {@code -} for any other entity. */
    private static String mobLine(ServerLevel level, Entity entity) {
        return BossAPI.roster(level).flatMap(roster -> roster.bossFor(entity.getUUID()))
                .map(boss -> "#" + Ids.shortId(boss.bossId()) + " " + boss.kind() + ", " + boss.describe())
                .orElse("-");
    }

    /** What the {@code Boss} knows about a record; the command's header already names the record's id and kind. */
    private static String recordLine(ServerLevel level, UUID bossId) {
        return BossAPI.roster(level).flatMap(roster -> roster.boss(bossId))
                .map(Boss::describe)
                .orElse("no Boss yet (its entry is not whole)");
    }
}
