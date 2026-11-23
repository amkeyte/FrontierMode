package com.arryn.frontiermode.diorite.common;

import com.arryn.satchel.common.tunable.Tunable;

/**
 * The Diorite Striker's and the Edge Stone's tuning (diorite-tools.md): safe-baseline numbers, Tunable-backed and
 * constructed at each read.
 */
public final class DioriteRules {

    private DioriteRules() {
    }

    /** Size of each spark's dust particle. */
    public static float strikerSparkSize() {
        return new Tunable("strikerSparkSize", 0.25f, 4f, 2.0f).floatValue();
    }

    /** Radius (blocks) of the circle the sparks sit on, in the plane of the struck face. */
    public static float strikerCircleRadius() {
        return new Tunable("strikerCircleRadius", 0.05f, 2f, 0.35f).floatValue();
    }

    /** Sparks fired by a strike that has a color but no count (the Relevant Border is off the path). */
    public static int strikerColorOnlySparks() {
        return new Tunable("strikerColorOnlySparks", 1, 10, 3).intValue();
    }

    /** Ticks between one spark and the next, so each can be counted; 0 fires them all together. */
    public static int strikerSparkDelayTicks() {
        return new Tunable("strikerSparkDelayTicks", 0, 20, 1).intValue();
    }

    /** Power of the explosion a strike makes off the Overworld. */
    public static float strikerBlastPower() {
        return new Tunable("strikerBlastPower", 0.1f, 4f, 1.0f).floatValue();
    }

    /** Durability a strike costs off the Overworld, instead of 1. */
    public static int strikerBlastDurability() {
        return new Tunable("strikerBlastDurability", 1, 64, 16).intValue();
    }

    /** White poof particles an Edge Stone makes where it crosses the Frontier line. */
    public static int edgeStonePoofParticles() {
        return new Tunable("edgeStonePoofParticles", 1, 64, 12).intValue();
    }
}
