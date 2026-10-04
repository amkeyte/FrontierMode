package com.arryn.frontiermode.boss.common;

import com.arryn.frontiermode.border.common.BorderMathLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Which loot tables a defeated Keeper rolls (boss.md § Defeat loot), free of Minecraft types so plain
 * JUnit can test the choice. Names are relative to {@code frontiermode:keeper/}; the tables themselves
 * are vanilla-format data the mod ships.
 */
public final class KeeperLootTables {

    /** Waves past this reuse its table. */
    private static final int LAST_WAVE_TABLE = 4;
    /** The milestone path positions that have a table of their own; later milestones share one. */
    private static final int[] AUTHORED_MILESTONES = {5, 10, 15, 20};

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

    private static boolean isAuthoredMilestone(int p) {
        for (int m : AUTHORED_MILESTONES) {
            if (m == p) {
                return true;
            }
        }
        return false;
    }
}
