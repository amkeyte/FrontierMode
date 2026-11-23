package com.arryn.frontiermode.boss;

/**
 * What an {@link ArmorAnswer} rule returns for the players in a fight (boss.md § The ArmorAnswer ability): the armor
 * capability it read, and the multiplier that gives. A hit on the boss lands at one over the multiplier.
 *
 * @param capability the armor capability read off the players, from 0 to 1
 * @param multiplier how many times tougher the boss is for it, never below 1
 */
public record ArmorReading(double capability, double multiplier) {
}
