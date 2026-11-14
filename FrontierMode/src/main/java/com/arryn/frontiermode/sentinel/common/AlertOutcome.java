package com.arryn.frontiermode.sentinel.common;

/**
 * What turning a Sentinel on a player did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes).
 */
public enum AlertOutcome {
    /** The Sentinel now targets the player. */
    ALERTED,
    /** The Sentinel was left as it was: it is fighting an ally, or vanilla's targeting rules exclude the player. */
    LEFT_ALONE
}
