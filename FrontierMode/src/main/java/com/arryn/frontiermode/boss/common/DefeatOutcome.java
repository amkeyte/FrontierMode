package com.arryn.frontiermode.boss.common;

/**
 * What a kind's {@code onDefeat} reports back (wiki/frontiermode/architecture/boss.md § BossKind): whether the defeat went
 * through, and a message the command that forced it prints, in the kind's own words (the Keeper says its level and what
 * its cascade did). The combat path ignores it. Boss cannot hold Border's {@code Result}, so a kind builds its message
 * itself.
 *
 * @param wentThrough whether the defeat went through to the end of the kind's cascade; false when a step failed
 * @param message     what happened, for a command to print
 */
public record DefeatOutcome(boolean wentThrough, String message) {
}
