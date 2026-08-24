package com.arryn.satchel.common.newconfig;

/**
 * Retired by SAT_040 -- this class's entire contents (the {@code PlayerJig} load/tick/unload
 * logging) moved to {@link com.arryn.satchel.common.tracking.SatchelHealth}, under the same
 * registered {@code satcheltracker:player_tracker_*} bundle/fixture/jig IDs, staying {@code
 * SERVER}-applicability, plus a new violation check on teardown. Nothing in either repo calls
 * this class anymore.
 *
 * <p>
 * Left as an empty stub rather than deleted -- consistent with how SAT_039 retired {@code
 * MobTrackingModule}. Safe to delete outright; nothing references it.
 */
public final class PlayerTrackingModule {

    private PlayerTrackingModule() {
    }
}
