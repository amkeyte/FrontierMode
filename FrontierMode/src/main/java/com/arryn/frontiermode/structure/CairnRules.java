package com.arryn.frontiermode.structure;

import com.arryn.satchel.common.tunable.Tunable;

/**
 * Tunables for the Cairn's pulse, the numbers meant to be tuned by eye and ear in play. Accessor
 * methods, not fields: a {@link Tunable} is constructed fresh at point of use.
 */
public final class CairnRules {

    private CairnRules() {
    }

    /**
     * How many candidate spots a Cairn tries before giving up (cairn.md § Placement). One candidate
     * is tried per Cairn per tick, so a boss ringed by water or the Wilds gets fewer Cairns instead of
     * a placement loop that never ends.
     */
    public static int cairnMaxAttempts() {
        return new Tunable("cairnMaxAttempts", 10, 2000, 200).intValue();
    }

    /** Volume of the ping played at the cap when the pulse fires. */
    public static float pingVolume() {
        return new Tunable("pingVolume", 0f, 4f, 0.25f).floatValue();
    }

    /** Pitch of the ping played at the cap when the pulse fires. */
    public static float pingPitch() {
        return new Tunable("pingPitch", 0.5f, 2f, 1.0f).floatValue();
    }

    /**
     * The pulse stream's launch height range, in blocks: how far the arc rises before it bends into
     * the bearing. Rolled fresh every trigger; never depends on how far the target is.
     */
    public static float launchHeightMin() {
        return new Tunable("launchHeightMin", 0f, 32f, 3.0f).floatValue();
    }

    public static float launchHeightMax() {
        return new Tunable("launchHeightMax", 0f, 32f, 7.0f).floatValue();
    }

    /**
     * The pulse stream's length range after the bend, in blocks: how far it travels along the bearing
     * before ending. Well short of the boss's real distance -- enough to read the direction, not a
     * beam to the target. Rolled fresh every trigger.
     */
    public static float streamLengthMin() {
        return new Tunable("streamLengthMin", 0f, 64f, 5.0f).floatValue();
    }

    public static float streamLengthMax() {
        return new Tunable("streamLengthMax", 0f, 64f, 15.0f).floatValue();
    }
}
