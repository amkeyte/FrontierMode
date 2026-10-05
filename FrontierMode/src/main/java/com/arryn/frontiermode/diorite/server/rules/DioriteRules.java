package com.arryn.frontiermode.diorite.server.rules;

/** The Diorite Striker's tuning (diorite-tools.md). */
public interface DioriteRules {

    DioriteRules ACTIVE = new DefaultDioriteRules();

    /** Size of each spark's dust particle. */
    float strikerSparkSize();

    /** Sparks fired by a strike that has a color but no count (the Relevant Border is off the path). */
    int strikerColorOnlySparks();

    /** Ticks between one spark and the next, so each can be counted; 0 fires them all together. */
    int strikerSparkDelayTicks();

    /** Power of the explosion a strike makes off the Overworld. */
    float strikerBlastPower();

    /** Durability a strike costs off the Overworld, instead of 1. */
    int strikerBlastDurability();
}
