package com.arryn.frontiermode.boss.common;

/**
 * What counting a boss's loss against the repeat-death cap did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes).
 */
public enum LossOutcome {
    /** Under the cap: a respawn delay is running. */
    RESPAWN_PENDING,
    /** The cap was reached: auto-respawn is suspended until {@code /boss mob spawn}. */
    RESPAWN_SUSPENDED
}
