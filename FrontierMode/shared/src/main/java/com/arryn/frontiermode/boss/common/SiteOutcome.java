package com.arryn.frontiermode.boss.common;

/**
 * What a kind's {@link BossKind#prepareSite} answers before the spawn: the site is {@link #READY}, or the
 * base should {@link #WAIT} and ask again next tick (boss.md § Spawn).
 */
public enum SiteOutcome {

    /** The site is prepared; the base spawns the mob now. */
    READY,

    /** The site is not ready yet; the base does nothing this tick and asks again next tick. */
    WAIT
}
