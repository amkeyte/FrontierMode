package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class BordersRevisionMonitor {

    private final int intervalTicks;

    private final Map<Object, Long> lastRevs = new HashMap<>();
    private final Map<Object, TickThrottler> throttlers = new HashMap<>();

    public BordersRevisionMonitor(int intervalTicks) {
        this.intervalTicks = intervalTicks;
    }

    /* ---------------------------------------------------------------------
     * Public API (gated)
     * ------------------------------------------------------------------ */

    /**
     * Routes through {@code BorderAPI.INFO(level)} and {@link BordersInfoFacet#owner()} (a
     * package-private escape hatch built for exactly this: this class lives in
     * {@code border.common.fixture} alongside {@link BordersFixture}, so it can still poll the
     * fixture directly via {@link #poll(BordersFixture)} below without that type crossing this
     * method's own public boundary).
     *
     * @return {@link RevisionPoll#CHANGED} when the revision moved since it was last read (the poll consumes that
     *         read), {@link RevisionPoll#UNCHANGED} otherwise, including when the Borders are not available
     */
    public RevisionPoll poll(Level level) {
        Objects.requireNonNull(level, "level");

        return BorderAPI.INFO(level)
                .map(BordersInfoFacet::owner)
                .map(this::poll)
                .orElse(RevisionPoll.UNCHANGED);
    }

    public RevisionPoll poll(BordersFixture monitored) {
        Objects.requireNonNull(monitored, "monitored");

        var info  = monitored.INFO;
        var scope = info.scope();   // deterministic value-identity
        @SuppressWarnings("resource") var level = info.level();

        TickThrottler throttler = throttlers.computeIfAbsent(
                scope,
                s -> new TickThrottler(intervalTicks, level::getGameTime)
        );

        if (!throttler.allow()) {
            return RevisionPoll.UNCHANGED;
        }

        return checkChanged(monitored);
    }

    /* ---------------------------------------------------------------------
     * Internal logic (ungated)
     * ------------------------------------------------------------------ */

    private RevisionPoll checkChanged(BordersFixture monitored) {
        var info  = monitored.INFO;
        var scope = info.scope();

        long currentRev = info.revision();
        Long lastRev    = lastRevs.get(scope);

        if (lastRev == null || lastRev != currentRev) {
            lastRevs.put(scope, currentRev);
            return RevisionPoll.CHANGED;
        }

        return RevisionPoll.UNCHANGED;
    }
}
