package com.arryn.frontiermode.difficulty;

/**
 * Outcome of putting a mob on a rung of the stat ladder ({@link DifficultyAPI#applyFeralRung}). The ways
 * it can fail to apply are told apart so a caller can say which one happened.
 */
public enum RungOutcome {
    /** The rung was written and the mob's health scaled. */
    APPLIED,
    /** Another rung already holds the mob. The first rung to reach a mob wins, so it was left alone. */
    ALREADY_RUNG,
    /** The Overworld has no path tip yet (an empty path, or its Border facet is not ready), so there is no level to scale to. */
    NO_PATH_TIP
}
