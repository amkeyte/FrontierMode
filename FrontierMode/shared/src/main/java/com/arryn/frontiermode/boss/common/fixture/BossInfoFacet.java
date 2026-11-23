package com.arryn.frontiermode.boss.common.fixture;

import java.util.List;

/**
 * Boss's queries/diagnostics facet, mirroring {@code BordersInfoFacet}'s shape -- the
 * read-only tick-cadence queries {@code BOSS_JIG}'s own tick walks every cycle
 * ({@link #unmaterialized()}/{@link #unpositioned()}, see {@code boss.md}'s "Three questions,
 * three different mechanisms"). The Keeper's path/record reconciliation diagnostic lives with the
 * Keeper's records, on {@code KeeperFixture}.
 */
public final class BossInfoFacet {
    private final BossFixture setting;

    BossInfoFacet(BossFixture setting) {
        this.setting = setting;
    }

    /**
     * See {@link BossFixture#unmaterialized()}.
     */
    public List<BossRecord> unmaterialized() {
        return setting.unmaterialized();
    }

    /**
     * See {@link BossFixture#unpositioned()}.
     */
    public List<BossRecord> unpositioned() {
        return setting.unpositioned();
    }
}
