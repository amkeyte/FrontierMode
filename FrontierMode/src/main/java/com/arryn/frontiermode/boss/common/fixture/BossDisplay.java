package com.arryn.frontiermode.boss.common.fixture;

/**
 * Human-readable formatting helper for Boss command feedback -- presentation-only, mirrors
 * {@code BorderDisplay}'s role for Border.
 */
public final class BossDisplay {

    private BossDisplay() {
    }

    /**
     * @param listIndex the record's position in {@code BossFixture.all()} at the moment this is
     *                   called -- the same value the bare-position selector mode resolves
     *                   against (see {@code BossSelector}'s "Selector scheme"). Surfaced so a
     *                   specific record can be targeted with {@code /boss <cmd> <n>} without
     *                   counting list order by hand; same reshuffle caveat Border's own bare
     *                   INDEX mode carries -- not stable across mutations.
     */
    public static String fullInfo(BossRecord r, int listIndex) {
        // Border Pregeneration: position is nullable until BOSS_JIG's own tick finalizes it (see
        // BossRecord's own doc) -- a boss can sit unpositioned for a while, this is the normal,
        // expected state, not a rare edge case, so /boss info has to be able to show it plainly.
        String posText = r.positioned()
                ? r.position().getX() + ", " + r.position().getY() + ", " + r.position().getZ()
                : "(pending -- home border still pregenerating)";

        // FRO_082: borderId -- "none" for a hand-placed off-path boss (/boss add), the actual
        // border id for anything paired with real border-growth (see BossRecord's own doc).
        String borderText = r.borderId().map(Object::toString).orElse("none");

        return "idx=" + listIndex
                + " | id=" + r.displayBossId()
                + " | pos: " + posText
                + " | layer=" + r.layer()
                + " | border=" + borderText
                + " | defeated=" + !r.alive()
                + " | spawned=" + r.materialized()
                + (r.materialized() ? " | entity=" + r.bossEntityId() : "");
    }
}
