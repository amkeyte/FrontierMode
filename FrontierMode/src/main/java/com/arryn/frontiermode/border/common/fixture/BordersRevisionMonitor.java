package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.satchel.common.util.throttle.TickThrottler;
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

    public boolean poll(Level level) {
        Objects.requireNonNull(level, "level");

        var borders = BorderAPI.borders(level);

        return borders
                .map(this::poll)
                .orElse(false);
    }

    public boolean poll(BordersFixture monitored) {
        Objects.requireNonNull(monitored, "monitored");

        var info  = monitored.INFO;
        var scope = info.scope();   // deterministic value-identity
        var level = info.level();

        TickThrottler throttler = throttlers.computeIfAbsent(
                scope,
                s -> new TickThrottler(intervalTicks, level::getGameTime)
        );

        if (!throttler.allow()) {
            return false;
        }

        return checkChanged(monitored);
    }

    /* ---------------------------------------------------------------------
     * Internal logic (ungated)
     * ------------------------------------------------------------------ */

    private boolean checkChanged(BordersFixture monitored) {
        var info  = monitored.INFO;
        var scope = info.scope();

        long currentRev = info.revision();
        Long lastRev    = lastRevs.get(scope);

        if (lastRev == null || lastRev != currentRev) {
            lastRevs.put(scope, currentRev);
            return true;
        }

        return false;
    }
}
