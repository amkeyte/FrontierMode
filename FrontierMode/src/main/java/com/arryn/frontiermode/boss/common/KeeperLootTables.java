package com.arryn.frontiermode.boss.common;

import com.arryn.frontiermode.border.common.BorderMathLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Which loot tables a defeated Keeper rolls, and how its reward grows past path position 20 (boss.md
 * § Defeat loot), free of Minecraft types so plain JUnit can test the choice and the arithmetic. Names
 * are relative to {@code frontiermode:keeper/}; the tables themselves are vanilla-format data the mod
 * ships.
 */
public final class KeeperLootTables {

    /** Waves past this reuse its table. */
    private static final int LAST_WAVE_TABLE = 4;
    /** The milestone path positions that have a table of their own; later milestones share one. */
    private static final int[] AUTHORED_MILESTONES = {5, 10, 15, 20};
    /** Loot grows for each milestone passed beyond this path position. */
    private static final int GROWTH_AFTER = 20;
    /** Guards a product that is whole on paper but a hair under it in floating point (10 x 1.3). */
    private static final double COUNT_EPSILON = 1e-9;

    /** The table that stands in for each extra copy of an unstackable stack (boss.md § Defeat loot). */
    public static final String EXTRA_PIECE = "extra_piece";
    /** How many times a repeated unstackable item is re-rolled before the repeat is accepted. */
    public static final int MAX_EXTRA_REROLLS = 8;

    private KeeperLootTables() {
    }

    /**
     * The tables for a Keeper at {@code pathPosition} with {@code body}, under a growth wave of
     * {@code waveLength}. A milestone (the last step of its wave) rolls {@code milestone_<p>} at 5, 10, 15
     * and 20 and {@code milestone_late} for any other milestone; every other Keeper rolls
     * {@code wave_<w>}, {@code w = ceil(p / waveLength)}, capped at 4. On top of either: saplings at path
     * position 1 (guaranteed) and 2-4 (a chance), and the netherite hoe for a Killer Bunny. A boss with
     * no path position rolls {@code wave_1}.
     */
    public static List<String> names(OptionalInt pathPosition, String body, int waveLength) {
        List<String> names = new ArrayList<>();
        if (pathPosition.isEmpty()) {
            names.add("wave_1");
        } else {
            int p = Math.max(pathPosition.getAsInt(), 1);
            if (BorderMathLogic.waveStep(p, waveLength) == waveLength) {
                names.add(isAuthoredMilestone(p) ? "milestone_" + p : "milestone_late");
            } else {
                int wave = (p + waveLength - 1) / waveLength;
                names.add("wave_" + Math.min(wave, LAST_WAVE_TABLE));
            }
            if (p == 1) {
                names.add("sapling_first");
            } else if (p <= 4) {
                names.add("sapling_early");
            }
        }
        if ("killer_bunny".equals(body)) {
            names.add("killer_bunny");
        }
        return names;
    }

    /**
     * Whether the stacks rolled from {@code tableName} grow past path position 20: the chart's
     * {@code wave_*} bundles and {@code milestone_*} pieces. Not the saplings, which stop at path
     * position 4, and not the Killer Bunny's hoe, a trophy rather than part of the chart.
     */
    public static boolean growsPastTwenty(String tableName) {
        return tableName.startsWith("wave_") || tableName.startsWith("milestone_");
    }

    /**
     * The reward multiplier for a Keeper at {@code pathPosition}: {@code 1 + step x k}, where {@code k}
     * is how many milestones (the last step of a growth wave of {@code waveLength}) it has passed beyond
     * path position 20, never below 0. It steps at each milestone and is flat between them; with the
     * default wave of 5 that is {@code floor((p - 20) / 5)}. A Keeper with no path position gets 1.0.
     */
    public static double multiplier(OptionalInt pathPosition, int waveLength, double step) {
        if (pathPosition.isEmpty() || waveLength < 1) {
            return 1.0;
        }
        int milestonesPast = Math.max(0, pathPosition.getAsInt() / waveLength - GROWTH_AFTER / waveLength);
        return 1.0 + step * milestonesPast;
    }

    /** {@code count} grown by {@code multiplier}, rounded down. */
    public static int scaledCount(int count, double multiplier) {
        return Math.max(0, (int) Math.floor(count * multiplier + COUNT_EPSILON));
    }

    /**
     * Splits {@code total} items into stack sizes no larger than {@code maxStackSize}: full stacks, then
     * the remainder, the way vanilla's own splitter does.
     */
    public static List<Integer> splitCounts(int total, int maxStackSize) {
        int max = Math.max(1, maxStackSize);
        List<Integer> sizes = new ArrayList<>();
        for (int left = total; left > 0; left -= max) {
            sizes.add(Math.min(left, max));
        }
        return sizes;
    }

    /**
     * Rolls with {@code roll}, and while the result {@code isRepeat} re-rolls it, up to
     * {@code maxRerolls} more times; the last roll is returned whether or not it repeats.
     */
    public static <T> T rollDistinct(Supplier<T> roll, Predicate<T> isRepeat, int maxRerolls) {
        T rolled = roll.get();
        for (int rerolls = 0; rerolls < maxRerolls && isRepeat.test(rolled); rerolls++) {
            rolled = roll.get();
        }
        return rolled;
    }

    private static boolean isAuthoredMilestone(int p) {
        for (int m : AUTHORED_MILESTONES) {
            if (m == p) {
                return true;
            }
        }
        return false;
    }
}
