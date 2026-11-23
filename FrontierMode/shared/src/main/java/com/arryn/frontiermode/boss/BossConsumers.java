package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The boss module's global consumers, written once for both lines: one consumer of the Info report's {@link InfoRequest},
 * which adds the {@code boss:} line for an entity, and the consumer of {@link BossDied}, which
 * {@link BossJigHandlers#onBossDied} turns into a defeat or a loss. The three events the mob abilities hear
 * ({@link BossMount}, {@link BossTakingDamage} and {@link BossDamaged}) are heard on the boss's own scope, so they have
 * no global consumer. Each line's {@code BossIngress} installs these.
 */
public final class BossConsumers {

    private BossConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(InfoRequest.class, BossConsumers::onInfo);
        declared.listenForAll(BossDied.class, BossJigHandlers::onBossDied);
        return declared;
    }

    /**
     * Registers the boss answers; the work happens in the suppliers, once per report. The {@code boss:} line is for
     * a boss's mob (found through the roster) or a {@code BossRecord}, and {@code -} for an entity that is not a boss.
     */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Boss] InfoRequest reached its consumer");

        Level level = request.level();
        for (Object subject : request.subjects()) {
            if (subject instanceof Entity entity) {
                request.add(subject, "boss", () -> "  boss: " + mobLine(level, entity));
            } else if (subject instanceof BossRecord record) {
                request.add(subject, "boss", () -> "  boss: " + recordLine(level, record.bossId()));
            }
        }
    }

    /** What the {@code Boss} knows about the mob that is its body, with its id and kind; {@code -} for any other entity. */
    private static String mobLine(Level level, Entity entity) {
        return BossAPI.roster(level).flatMap(roster -> roster.bossFor(entity.getUUID()))
                .map(boss -> "#" + Ids.shortId(boss.bossId()) + " " + boss.kind() + ", " + boss.describe())
                .orElse("-");
    }

    /** What the {@code Boss} knows about a record; the command's header already names the record's id and kind. */
    private static String recordLine(Level level, UUID bossId) {
        return BossAPI.roster(level).flatMap(roster -> roster.boss(bossId))
                .map(Boss::describe)
                .orElse("no Boss yet (its entry is not whole)");
    }
}
