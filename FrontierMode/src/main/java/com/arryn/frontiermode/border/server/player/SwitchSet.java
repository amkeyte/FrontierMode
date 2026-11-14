package com.arryn.frontiermode.border.server.player;

/**
 * What setting the Frontier Sickness switch did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes).
 */
public enum SwitchSet {
    /** The switch was found and now holds the value. */
    SET,
    /** The switch could not be found: the Overworld's scope is not ready. */
    NOT_AVAILABLE
}
