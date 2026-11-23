package com.arryn.frontiermode.core;

/**
 * Whether a mob enters a module's presence (wiki/frontiermode/architecture/module-graph.md § Operation outcomes): what a {@code Presence.Hooks.adopt} answers, and what the
 * code that decides a mob belongs there answers.
 */
public enum Adoption {
    /** The mob belongs in presence. */
    ADOPT,
    /** The mob is left out. */
    LEAVE_OUT
}
