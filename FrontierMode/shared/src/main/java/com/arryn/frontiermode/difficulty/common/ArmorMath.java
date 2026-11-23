package com.arryn.frontiermode.difficulty.common;

/**
 * The armor read's arithmetic (difficulty.md § Armor answers back), free of Minecraft types so plain JUnit can test it.
 * Vanilla's two reductions are written out here once and not taken from {@code CombatRules}, so a port that changes
 * vanilla's call touches the class that reads the player and nothing in this arithmetic.
 *
 * <p>A kit's <em>cut</em> is the share of one reference hit the armor and the Protection enchantment remove between
 * them; its <em>capability</em> is that cut as a share of the best vanilla kit's, clamped to 0 (bare skin) to 1.
 */
public final class ArmorMath {

    /** Armor points of the best vanilla kit: a full set of Netherite. */
    public static final double BEST_ARMOR = 20;
    /** Armor toughness of the best vanilla kit. */
    public static final double BEST_TOUGHNESS = 12;
    /** Protection points of the best vanilla kit: Protection IV on each of four pieces. */
    public static final double BEST_PROTECTION = 16;

    private ArmorMath() {
    }

    /**
     * A kit's capability: its cut of a {@code referenceHit}-point hit as a share of the best kit's, from 0 to 1. A cut
     * is {@code 1 - armorFactor x protectionFactor}, where {@code armorFactor = 1 - clamp(armor - hit / (2 + toughness / 4),
     * armor / 5, 20) / 25} and {@code protectionFactor = 1 - min(max(protection, 0), 20) x min(armor / 20, 1) / 25}.
     * Protection counts in proportion to the armor it sits on, so none counts with no armor and all of it counts at 20
     * armor points.
     */
    public static double capability(double armor, double toughness, double protection, double referenceHit) {
        double best = cut(BEST_ARMOR, BEST_TOUGHNESS, BEST_PROTECTION, referenceHit);
        return clamp(cut(armor, toughness, protection, referenceHit) / best, 0.0, 1.0);
    }

    /**
     * What a use multiplies by: {@code counter ^ max(0, capability - expected)}, never below 1. A {@code counter} of 1
     * always answers 1.
     *
     * @throws IllegalArgumentException if {@code counter} is below 1, which is a programming error
     */
    public static double multiplier(double counter, double capability, double expected) {
        if (!(counter >= 1.0)) {
            throw new IllegalArgumentException("[Difficulty] ArmorMath.multiplier(): counter must be at least 1, got " + counter);
        }
        return Math.pow(counter, Math.max(0.0, capability - expected));
    }

    private static double cut(double armor, double toughness, double protection, double referenceHit) {
        return 1.0 - armorFactor(armor, toughness, referenceHit) * protectionFactor(armor, protection);
    }

    // Vanilla's armor reduction: the armor removes at least a fifth of its points' share, and at most 20 of 25.
    private static double armorFactor(double armor, double toughness, double referenceHit) {
        double removed = clamp(armor - referenceHit / (2.0 + toughness / 4.0), armor / 5.0, 20.0);
        return 1.0 - removed / 25.0;
    }

    // Vanilla's Protection reduction, with its points scaled by the armor's share of 20 points.
    private static double protectionFactor(double armor, double protection) {
        double points = Math.min(Math.max(protection, 0.0), 20.0) * Math.min(armor / 20.0, 1.0);
        return 1.0 - points / 25.0;
    }

    // The same shape as vanilla's clamp: the lower bound wins when it is the higher of the two.
    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
