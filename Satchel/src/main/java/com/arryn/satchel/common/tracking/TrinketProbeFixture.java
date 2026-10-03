package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.fixture.SatchelFixture;

/**
 * State for {@link TrinketHealthCheck}'s probe Trinket: a persisted count of detach writes (the
 * proof a write made in {@code onDetach} was saved, read back on the next load) and a runtime tick
 * count (the proof the Trinket's two tick phases bracket the fixtures' tick).
 */
public final class TrinketProbeFixture extends SatchelFixture {

    private long detaches;
    private long ticks;

    public TrinketProbeFixture() {
        registerLong("detaches", () -> detaches, val -> detaches = val);
    }

    public void countDetach() {
        detaches++;
        // A bare field write doesn't tell the bundle it changed; without this the terminal flush
        // at unload skips the bundle as clean.
        markDirty();
    }

    public long detaches() {
        return detaches;
    }

    public long ticks() {
        return ticks;
    }

    @Override
    public void onJigTick() {
        ticks++;
    }

    @Override
    public String toString() {
        return "TrinketProbeFixture{detaches=" + detaches + ", ticks=" + ticks + '}';
    }
}
