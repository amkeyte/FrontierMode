package com.arryn.satchel.common.lifecycle;

import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

import java.util.Objects;

/**
 * Controls the lifecycle of a {@link LogicalFoundation}.
 *
 * <p>
 * Answers the question:
 * "Is Satchel operational on this logical side?"
 *
 * <p>
 * Lifecycle is derived, not commanded.
 *
 * <pre>
 *   NEW ──► STARTED ──► STOPPED
 * </pre>
 *
 * <p>
 * Transitions occur during {@link #pulse()} based on observable conditions.
 */
public final class FoundationLifecycleDispatcher {

    private final LogicalFoundation foundation;

    private Phase phase = Phase.NEW;

    public FoundationLifecycleDispatcher(LogicalFoundation foundation) {
        this.foundation = Objects.requireNonNull(foundation, "foundation");
    }

    /* =============================================================
     * Execution driver
     * ========================================================== */

    /**
     * Called once per execution pulse.
     * <p>
     * This method is responsible for detecting lifecycle transitions
     * and emitting events exactly once per edge.
     */
    public void pulse() {

        // NEW → STARTED
        if (phase == Phase.NEW) {
            if (canStart()) {
                phase = Phase.STARTED;
                foundation.eventBus()
                        .post(new SatchelEvent.Started(foundation.side()));
            }
            return;
        }

        // STARTED → STOPPED
        if (phase == Phase.STARTED) {

            for (var jInfo:foundation.jigInfos()){
                for (ScopeInfo sInfo : jInfo.scopeInfos()) {
                    jInfo.jig.handleExecutionPulse(sInfo);
                    jInfo.jig.onTick(sInfo);
                }
            }


            if (shouldStop()) {
                for (var jInfo:foundation.jigInfos()){
                    for (ScopeInfo sInfo : jInfo.scopeInfos()) {
                        jInfo.jig.onUnload(sInfo);
                    }
                }
                foundation.eventBus()
                        .post(new SatchelEvent.Stopped(foundation.side()));
                phase = Phase.STOPPED;
            }
        }
    }

    /* =============================================================
     * Transition conditions
     * ========================================================== */

    /**
     * Determines whether the foundation is ready to start.
     * <p>
     * This should reflect "Satchel is operational", not "Satchel exists".
     */
    private boolean canStart() {
        // Event bus + scopeInfo lifecycle must be installed
        return foundation.eventBus() != null
                && foundation.scopeLifecycle() != null;
    }

    /**
     * Determines whether the foundation should stop.
     * <p>
     * Default: never auto-stop.
     * (Ingress teardown or JVM shutdown may extend this later.)
     */
    private boolean shouldStop() {
        return false;
    }

    /* =============================================================
     * State queries
     * ========================================================== */

    public boolean isStarted() {
        return phase == Phase.STARTED;
    }

    public boolean isStopped() {
        return phase == Phase.STOPPED;
    }

    public boolean isNew() {
        return phase == Phase.NEW;
    }

    /* =============================================================
     * Internal phase
     * ========================================================== */

    @Override
    public String toString() {
        return "FoundationLifecycle[" + phase + "]";
    }

    private enum Phase {
        NEW,
        STARTED,
        STOPPED
    }
}
