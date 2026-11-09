package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.frontiermode.keeper.KeeperCreated;
import com.arryn.frontiermode.keeper.KeeperDefeated;
import com.arryn.frontiermode.keeper.KeeperHit;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The holder class for the Sentinel module's ingress (wiki/frontiermode/architecture/sentinels.md § Class map): the three
 * consumers of the Keeper's events ({@link KeeperCreated}, {@link KeeperHit}, {@link KeeperDefeated}; keeper.md §
 * Keeper events) and the consumer of the Info report's {@link InfoRequest}, which adds the {@code sentinel:} line.
 * {@link SentinelModule#init()} registers {@link #build()} through a lambda, so this class loads when the server side
 * boots.
 */
final class SentinelIngress {

    private SentinelIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.SENTINEL_INGRESS);
        ingress.listenForAll(KeeperCreated.class, SentinelIngress::onKeeperCreated);
        ingress.listenForAll(KeeperHit.class, SentinelIngress::onKeeperHit);
        ingress.listenForAll(KeeperDefeated.class, SentinelIngress::onKeeperDefeated);
        ingress.listenForAll(InfoRequest.class, SentinelIngress::onInfo);
        return ingress;
    }

    /**
     * A Keeper was created for a Border (keeper.md § Keeper events): the Border gets its two Sentinel curves if it
     * lacks them. Idempotent, so a restart or a second creation is a no-op.
     */
    private static void onKeeperCreated(KeeperCreated created) {
        OUT.debug("[Sentinel] KeeperCreated reached its consumer");
        SentinelConversion.ensureSentinelCurves(created.level(), created.borderId());
    }

    /**
     * A player hit a Keeper (keeper.md § Keeper events), once per new credited hit: that Keeper's Sentinels near the
     * fight turn on the player (sentinels.md § Defending the boss).
     */
    private static void onKeeperHit(KeeperHit hit) {
        OUT.debug("[Sentinel] KeeperHit reached its consumer");
        Level level = hit.level();
        SentinelPresence.of(level).ifPresent(
                presence -> presence.alertSentinelsOf(level, hit.bossId(), hit.mob(), hit.player()));
    }

    /**
     * A Keeper was defeated, a real defeat only (keeper.md § Keeper events): its present Sentinels are queued for the
     * staggered kill, credited to the player who defeated it, or uncredited for a forced defeat (sentinels.md § When
     * the boss is defeated).
     */
    private static void onKeeperDefeated(KeeperDefeated defeated) {
        OUT.debug("[Sentinel] KeeperDefeated reached its consumer");
        SentinelPresence.of(defeated.level()).ifPresent(presence -> presence.doomSentinelsOf(
                defeated.bossId(), defeated.credited().map(Player::getUUID).orElse(null)));
    }

    /** Registers the Sentinel answer; the work happens in the supplier, once per report. */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Sentinel] InfoRequest reached its consumer");

        for (Object subject : request.subjects()) {
            if (subject instanceof Mob mob) {
                request.add(subject, "sentinel", () -> sentinelLine(mob));
            }
        }
    }

    /** The Sentinel line, or null for a mob that carries no Sentinel record. */
    private static String sentinelLine(Mob mob) {
        return SentinelData.read(mob)
                .map(facts -> String.format(
                        "  Sentinel: scale %.2f, boss #%s", facts.scale(), Ids.shortId(facts.bossId())))
                .orElse(null);
    }
}
