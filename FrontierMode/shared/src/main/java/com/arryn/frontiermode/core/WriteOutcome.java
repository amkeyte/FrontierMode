package com.arryn.frontiermode.core;

/**
 * What a write-if-absent of a mob's record did (wiki/frontiermode/architecture/module-graph.md § Operation outcomes). Built from Satchel's {@code MobData.writeIfAbsent}.
 */
public enum WriteOutcome {
    /** The mob carried no record and now has one. */
    WRITTEN,
    /** The mob already carried a record, left as it was. */
    ALREADY_PRESENT
}
