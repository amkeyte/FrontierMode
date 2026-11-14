package com.arryn.frontiermode.border.common.fixture;

/**
 * What starting a Border's pregeneration job did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes).
 */
public enum PregenStart {
    /** No job existed for the Border and one has begun. */
    STARTED,
    /** A job already exists, in progress or complete, and is left as it is. */
    ALREADY_STARTED
}
