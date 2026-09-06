package com.arryn.frontiermode.boss.common.fixture;

/**
 * Outcome of a {@link BossSpawnFacet#forceMaterialize} call -- distinguishable cases so
 * {@code /boss mob spawn}'s chat feedback can give a specific reason in each situation.
 */
public enum MaterializeOutcome {
    SPAWNED,
    ALREADY_MATERIALIZED,
    NO_RECORD,
    DECLINED
}
