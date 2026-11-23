package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.border.server.BorderChangeRequest;
import com.arryn.frontiermode.boss.Boss;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The Keeper's global consumers (wiki/frontiermode/architecture/keeper.md § Class map), written once for both lines: one
 * consumer of Border's {@link BorderChangeRequest}, which refuses the removal of a Border that has a Keeper, and one of
 * the Info report's {@link InfoRequest}, which adds the {@code keeper:} line. The two events the Keeper's mob abilities
 * hear, {@link KeeperConversion} and {@link KeeperTargetChange}, are heard on the boss's own scope. Each line's
 * {@code KeeperIngress} installs these.
 */
public final class KeeperConsumers {

    private KeeperConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(BorderChangeRequest.class, KeeperConsumers::onBorderChangeRequest);
        declared.listenForAll(InfoRequest.class, KeeperConsumers::onInfo);
        return declared;
    }

    /**
     * A Border that has a Keeper record is held still (keeper.md § A Keeper holds its Border still): the request is
     * refused for any record, alive or defeated, materialized or not, positioned or not.
     * The reason names the Border and the Keeper by short id and says to {@code /boss delete} the Keeper first. A
     * Keeper fixture that is not resolvable yet refuses nothing: standby, don't crash.
     */
    private static void onBorderChangeRequest(BorderChangeRequest request) {
        OUT.debug("[Keeper] BorderChangeRequest reached its consumer");
        UUID borderId = request.border().id();
        KeeperAPI.keepers(request.level())
                .flatMap(keepers -> keepers.firstForBorder(borderId))
                .ifPresent(keeper -> request.refuse("Border " + Ids.shortId(borderId) + " cannot be removed: Keeper "
                        + Ids.shortId(keeper.bossId()) + " holds it. /boss delete the Keeper first"));
    }

    /**
     * Registers the Keeper answer; the work happens in the supplier, once per report. The {@code keeper:} line is for a
     * Keeper's mob (found through the roster) or its {@code BossRecord} (keeper.md § Info lines), and left out for any
     * other subject.
     */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Keeper] InfoRequest reached its consumer");

        Level level = request.level();
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
    private static String keeperLine(Level level, Optional<UUID> bossId) {
        return bossId.flatMap(id -> KeeperAPI.keepers(level).flatMap(keepers -> keepers.get(id)))
                .map(keeper -> String.format(
                        "  keeper: layer %d, path position %d, body %s, border %s, platform %s, expects %.2f",
                        keeper.layer(), KeeperAPI.pathPositionOf(keeper),
                        keeper.body().isEmpty() ? "-" : keeper.body(),
                        keeper.borderId().map(Ids::shortId).orElse("none"),
                        keeper.platformBuilt()
                                ? keeper.platformPos().getX() + ", " + keeper.platformPos().getY() + ", "
                                        + keeper.platformPos().getZ()
                                : "-",
                        KeeperAPI.expectedCapability(keeper)))
                .orElse(null);
    }
}
