package com.arryn.frontiermode.keeper.common;

/**
 * How bright a Keeper's eyes are, a decision the draw class takes from here (keeper.md § Client-side eyes and flash): the
 * brightness scales with the body's health over its maximum, down to {@link #FLOOR} so a hurt Keeper stays findable.
 * Each line's {@code BossEyesLayer} turns the number into the colour arguments of its own draw call.
 */
public final class EyeBrightness {

    /** How bright the eyes are at no health: they never go fully dark. Set by eye. */
    public static final float FLOOR = 0.25F;

    private EyeBrightness() {
    }

    /**
     * The brightness for a body at {@code health} of {@code maxHealth}: {@link #FLOOR} plus the rest of the range
     * times the health fraction, which is clamped to 0 to 1. Full health is 1, none is the floor.
     */
    public static float of(float health, float maxHealth) {
        float fraction = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
        return FLOOR + (1.0F - FLOOR) * fraction;
    }
}
