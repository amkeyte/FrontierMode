package com.arryn.frontiermode.boss.common;

/**
 * What a kind's {@code onCreate} did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes): whether it wrote its own record for the boss. A rejection is
 * logged by the kind, with its reason, and nothing is written.
 */
public enum CreateOutcome {
    /** The kind wrote its record. */
    CREATED,
    /** The kind refused: an argument it cannot accept, or its own fixture not being available yet. */
    REJECTED
}
