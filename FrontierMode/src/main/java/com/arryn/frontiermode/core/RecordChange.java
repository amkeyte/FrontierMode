package com.arryn.frontiermode.core;

/**
 * What a change to one record did: the boss and Keeper records' changes and the Border removals answer with it
 * (wiki/frontiermode/architecture/module-graph.md § Operation outcomes). Nothing changed unless it is {@link #DONE}.
 */
public enum RecordChange {
    /** The record existed and was changed, or removed. */
    DONE,
    /** There is no such record. */
    NO_RECORD,
    /** The record exists but is in the wrong state for this change (already defeated, already materialized,
     * already positioned, already has its platform). A removal never answers this. */
    WRONG_STATE
}
