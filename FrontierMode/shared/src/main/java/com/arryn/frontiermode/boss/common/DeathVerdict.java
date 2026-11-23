package com.arryn.frontiermode.boss.common;

/**
 * A kind's answer to {@link BossKind#judgeDeath}: whether a death is a {@link #DEFEAT} or a {@link #LOSS}
 * (boss.md § What counts as a defeat).
 */
public enum DeathVerdict {

    /** The base marks the record defeated, schedules the flush, then calls the kind's {@code onDefeat}. */
    DEFEAT,

    /** The base takes the loss path: no payout, and a {@code Persistent} boss returns to unmaterialized. */
    LOSS
}
