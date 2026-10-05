package com.arryn.frontiermode.boss.common.fixture;

/**
 * Outcome of a {@link BossSpawnFacet#forceMaterialize} call -- distinguishable cases so
 * {@code /boss mob spawn}'s chat feedback can give a specific reason in each situation.
 */
public enum MaterializeOutcome {
    /** A new entity was spawned at the record's spot. */
    SPAWNED,
    /** The record said it had an entity but none was present (gone, converted, unloaded): a fresh one replaced it. */
    SPAWNED_REPLACING,
    /** The boss is alive out in the world, so it was teleported back to its spot instead. */
    TELEPORTED_HOME,
    NO_RECORD,
    /** The home Border is still pregenerating, so the record has no spot yet. */
    NOT_POSITIONED,
    /** The spot was reached but the entity couldn't be created or added. */
    DECLINED
}
