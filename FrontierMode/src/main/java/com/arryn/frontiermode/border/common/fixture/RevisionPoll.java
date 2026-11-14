package com.arryn.frontiermode.border.common.fixture;

/**
 * What polling the Borders' revision did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes). Polling reads the revision, so it is made once per look.
 */
public enum RevisionPoll {
    /** The revision is the one last read, or the poll was throttled, or the Borders are not available. */
    UNCHANGED,
    /** The revision moved since it was last read, and the new one is now the one read. */
    CHANGED
}
