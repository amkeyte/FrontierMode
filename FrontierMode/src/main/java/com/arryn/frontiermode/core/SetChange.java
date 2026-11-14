package com.arryn.frontiermode.core;

/**
 * What adding to or removing from a set did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes): whether the set changed.
 */
public enum SetChange {
    /** The entry was added, or removed. */
    CHANGED,
    /** The set already had the answer: the entry was already there, or was not there to remove. */
    UNCHANGED
}
