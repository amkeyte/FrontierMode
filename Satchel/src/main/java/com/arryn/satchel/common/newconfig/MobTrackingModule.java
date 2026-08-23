package com.arryn.satchel.common.newconfig;

/**
 * Retired by SAT_039 -- this class's entire contents (the {@code MobJig} health check, the
 * {@code watch}/{@code unwatch}/{@code currentInterests} surface, and the bundle/tracker
 * plumbing) moved to {@link com.arryn.satchel.common.tracking.SatchelHealth}, under the same
 * registered {@code satchelmobtracker:*} bundle/fixture/jig IDs, with {@code sideApplicability}
 * widened from {@code SERVER} to {@code BOTH}. {@code MobTrackCommands} now calls
 * {@code SatchelHealth} directly; nothing in either repo calls this class anymore.
 *
 * <p>
 * Left as an empty stub rather than deleted -- this session's tooling has no file-delete access
 * to the project folder. Safe to delete outright; nothing references it.
 */
public final class MobTrackingModule {

    private MobTrackingModule() {
    }
}
