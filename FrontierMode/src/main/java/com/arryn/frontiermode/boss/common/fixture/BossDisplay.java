package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.satchel.common.util.Ids;

import java.util.Optional;

/**
 * Human-readable formatting helper for Boss command feedback -- presentation-only, mirrors
 * {@code BorderDisplay}'s role for Border.
 */
public final class BossDisplay {

    private BossDisplay() {
    }

    /**
     * @param keeper    the boss's Keeper record, empty when its entry is not whole yet; its layer and border
     *                  show as {@code -} then
     * @param listIndex the record's position in {@code BossFixture.all()} at the moment this is
     *                   called -- the same value the bare-position selector mode resolves
     *                   against (see {@code BossSelector}'s "Selector scheme"). Surfaced so a
     *                   specific record can be targeted with {@code /boss <cmd> <n>} without
     *                   counting list order by hand; same reshuffle caveat Border's own bare
     *                   INDEX mode carries -- not stable across mutations.
     */
    public static String fullInfo(BossRecord r, Optional<KeeperRecord> keeper, int listIndex) {
        // Border Pregeneration: position is nullable until BOSS_JIG's own tick finalizes it (see
        // BossRecord's own doc) -- a boss can sit unpositioned for a while, this is the normal,
        // expected state, not a rare edge case, so /boss info has to be able to show it plainly.
        String posText = r.positioned()
                ? r.position().getX() + ", " + r.position().getY() + ", " + r.position().getZ()
                : "(pending -- home border still pregenerating)";

        // "none" for a hand-placed off-path boss (/boss add), the actual border id for anything paired with
        // real border-growth (see KeeperRecord's own doc), shown as the short id like the boss id and the
        // entity; "-" while the Keeper record is not there yet.
        String borderText = keeper.map(k -> k.borderId().map(Ids::shortId).orElse("none")).orElse("-");
        String layerText = keeper.map(k -> String.valueOf(k.layer())).orElse("-");

        return "idx=" + listIndex
                + " | id=" + r.displayBossId()
                + " | pos: " + posText
                + " | layer=" + layerText
                + " | border=" + borderText
                + " | defeated=" + !r.alive()
                + " | spawned=" + r.materialized()
                + (r.materialized() ? " | entity=" + Ids.shortId(r.bossEntityId()) : "");
    }
}
