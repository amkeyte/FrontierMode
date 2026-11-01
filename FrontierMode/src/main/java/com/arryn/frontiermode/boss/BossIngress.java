package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Optional;
import java.util.UUID;

/**
 * The holder class for the boss module's ingress: one consumer of the Info report's {@link InfoRequest},
 * which adds the {@code boss:} line for an entity and the {@code Sentinel:} line for a Sentinel, and the two module
 * events the mob abilities hear, {@link BossMount} and {@link BossDamaged}.
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
     * Registers the boss answers; the work happens in the suppliers, once per report. The {@code boss:}
     * line is {@code -} for an entity that is not a boss, and the Sentinel line is left out for a mob
     * that is not a Sentinel.
     */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Boss] InfoRequest reached its consumer");

        ServerLevel level = request.level();
        for (Object subject : request.subjects()) {
            if (subject instanceof Entity entity) {
                request.add(subject, "boss", () -> "  boss: " + bossLine(level, entity.getUUID()));
            }
            if (subject instanceof Mob mob) {
                request.add(subject, "boss", () -> sentinelLine(mob));
            }
        }
    }

    private static String bossLine(ServerLevel level, UUID entityId) {
        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            return "-";
        }
        BossFixture fixture = fixtureOpt.get();
        for (BossRecord record : fixture.all()) {
            if (entityId.equals(record.bossEntityId())) {
                Optional<KeeperRecord> keeperOpt = KeeperAPI.keepers(level).flatMap(k -> k.get(record.bossId()));
                if (keeperOpt.isEmpty()) {
                    return String.format("#%s %s%s, no Keeper record yet", Ids.shortId(record.bossId()),
                            record.alive() ? "alive" : "defeated", record.materialized() ? ", materialized" : "");
                }
                KeeperRecord keeper = keeperOpt.get();
                return String.format("#%s layer %d path position %d body %s border %s %s%s scale %.2f",
                        Ids.shortId(record.bossId()), keeper.layer(), KeeperAPI.pathPositionOf(keeper),
                        keeper.body().isEmpty() ? "-" : keeper.body(),
                        keeper.borderId().map(Ids::shortId).orElse("-"),
                        record.alive() ? "alive" : "defeated",
                        record.materialized() ? ", materialized" : "",
                        fixture.RULES.keeperScale(keeper));
            }
        }
        return "-";
    }

    /** The Sentinel line, or null for a mob that carries no Sentinel record. */
    private static String sentinelLine(Mob mob) {
        return SentinelData.read(mob)
                .map(facts -> String.format(
                        "  Sentinel: scale %.2f, boss #%s", facts.scale(), Ids.shortId(facts.bossId())))
                .orElse(null);
    }
}
