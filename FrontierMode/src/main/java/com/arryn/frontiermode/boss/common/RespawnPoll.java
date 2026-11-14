package com.arryn.frontiermode.boss.common;

/**
 * What polling a boss for its respawn did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes). Polling counts the respawn delay down, so it is made once per
 * tick.
 */
public enum RespawnPoll {
    /** No respawn is pending: the boss may materialize. */
    NOT_PENDING,
    /** The respawn delay is still running, or auto-respawn is suspended. */
    WAITING,
    /** The respawn delay has just run out and was consumed: the boss may materialize. */
    READY
}
